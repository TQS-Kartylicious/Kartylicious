import argparse
import itertools
import json
import os
import threading
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt
from paho.mqtt import publish

PREFIX = "kartylicious/karts"
BROKER = os.environ.get("FLEET_MQTT_HOST", "mqtt")
CONTROL = "kartylicious/simulator/control"
DEFAULT_FLEET = {
    "K-101": (84, "central-square", True, False),
    "K-102": (20, "central-square", True, False),
    "K-103": (19, "central-square", True, False),
    "K-104": (71, None, False, False),
    "K-105": (63, "central-square", True, False),
    "K-106": (92, "central-square", True, False),
    "K-201": (18, "riverside-park", True, False),
    "K-202": (66, "riverside-park", True, False),
    "K-301": (56, "university-gardens", True, False),
    "K-302": (47, "university-gardens", True, False),
    "K-303": (73, "university-gardens", True, True),
}


def run():
    configured = os.environ.get("FLEET_SIMULATOR_FLEET")
    selection = (
        json.loads(configured) if configured
        else {kart: state[0] for kart, state in DEFAULT_FLEET.items()}
    )
    fleet = {}
    for kart, battery in selection.items():
        if type(battery) is not int or not 0 <= battery <= 100:
            raise ValueError(f"Invalid battery level for {kart}")
        _, hub, locked, fault = DEFAULT_FLEET.get(kart, (0, None, True, False))
        fleet[kart] = {
            "batteryLevel": battery, "hubId": hub, "locked": locked, "criticalFault": fault
        }
    rate = float(os.environ.get("FLEET_SIMULATOR_RATE", "10"))
    if not fleet or rate <= 0:
        raise ValueError("Configure at least one kart and a positive publication rate")
    guard = threading.Lock()
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="kartylicious-fleet-simulator")

    def connected(connection, _userdata, _flags, reason, _properties):
        if not reason.is_failure:
            connection.subscribe([(CONTROL, 1), (f"{PREFIX}/+/lock-command", 1)])

    def received(_connection, _userdata, message):
        try:
            with guard:
                if message.topic == CONTROL:
                    command = json.loads(message.payload)
                    kart = fleet[command.pop("kartId")]
                    kart.update({key: command[key] for key in kart.keys() & command.keys()})
                else:
                    kart_id = message.topic.split("/")[-2]
                    if kart_id in fleet and message.payload in (b"true", b"false"):
                        fleet[kart_id]["locked"] = message.payload == b"true"
        except (KeyError, ValueError, TypeError):
            print("Ignored invalid simulator control")

    client.on_connect = connected
    client.on_message = received
    client.connect_async(BROKER, 1883)
    client.loop_start()
    try:
        for kart_id in itertools.cycle(fleet):
            tick = time.monotonic()
            if client.is_connected():
                with guard:
                    observation = dict(fleet[kart_id])
                observation["recordedAt"] = datetime.now(timezone.utc).isoformat(timespec="microseconds")
                client.publish(f"{PREFIX}/{kart_id}/telemetry", json.dumps(observation), qos=1)
            time.sleep(max(0, 1 / rate - (time.monotonic() - tick)))
    finally:
        client.loop_stop()
        client.disconnect()


def control():
    parser = argparse.ArgumentParser(description="Update synthetic observations for a configured kart")
    parser.add_argument("command", choices=["set"])
    parser.add_argument("kart_id")
    parser.add_argument("--hub", help="Seeded hub ID or 'outside'")
    parser.add_argument("--battery", type=int, choices=range(101), metavar="0..100")
    parser.add_argument("--locked", choices=["true", "false"])
    parser.add_argument("--critical-fault", choices=["true", "false"])
    args = parser.parse_args()
    event = {"kartId": args.kart_id}
    if args.hub is not None:
        event["hubId"] = None if args.hub == "outside" else args.hub
    if args.battery is not None:
        event["batteryLevel"] = args.battery
    if args.locked is not None:
        event["locked"] = args.locked == "true"
    if args.critical_fault is not None:
        event["criticalFault"] = args.critical_fault == "true"
    if len(event) == 1:
        parser.error("Specify at least one observation field")
    publish.single(CONTROL, json.dumps(event), qos=1, hostname=BROKER)


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1:
        control()
    else:
        run()
