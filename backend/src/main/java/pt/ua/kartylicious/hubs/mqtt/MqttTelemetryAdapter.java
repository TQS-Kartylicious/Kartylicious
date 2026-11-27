package pt.ua.kartylicious.hubs.mqtt;

import jakarta.annotation.PreDestroy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pt.ua.kartylicious.hubs.FleetTelemetryService;
import pt.ua.kartylicious.hubs.TelemetryObservation;
import pt.ua.kartylicious.hubs.VehicleLockCommand;
import tools.jackson.databind.json.JsonMapper;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "fleet.mqtt.enabled", havingValue = "true")
public class MqttTelemetryAdapter {
  private static final Logger LOGGER = LoggerFactory.getLogger(MqttTelemetryAdapter.class);
  private static final String TOPIC_PREFIX = "kartylicious/karts/";
  private static final Pattern TOPIC = Pattern.compile(
      TOPIC_PREFIX + "([A-Za-z0-9_-]{1,64})/telemetry");
  private final MqttAsyncClient client;
  private final FleetTelemetryService telemetry;
  private final JsonMapper json;
  private final Clock clock;
  private final Timer lag;
  private volatile boolean ready;

  public MqttTelemetryAdapter(
      @Value("${fleet.mqtt.url}") String url,
      FleetTelemetryService telemetry,
      JsonMapper json,
      MeterRegistry meters,
      Clock clock) throws MqttException {
    this.client = new MqttAsyncClient(url, "kartylicious-" + UUID.randomUUID(), new MemoryPersistence());
    this.telemetry = telemetry;
    this.json = json;
    this.clock = clock;
    this.lag = meters.timer("fleet.telemetry.processing.lag");
  }

  @EventListener(ApplicationReadyEvent.class)
  void start() {
    ready = true;
  }

  @Scheduled(fixedDelay = 2000)
  void connect() {
    if (!ready || client.isConnected()) {
      return;
    }
    try {
      var options = new MqttConnectOptions();
      options.setConnectionTimeout(3);
      client.connect(options).waitForCompletion(4000);
      client.subscribe(TOPIC_PREFIX + "+/telemetry", 1,
          (topic, message) -> receive(topic, message.getPayload())).waitForCompletion(4000);
    } catch (MqttException exception) {
      LOGGER.warn("Fleet MQTT connection or subscription failed: {}", exception.getMessage());
      if (client.isConnected()) {
        try {
          client.disconnectForcibly();
        } catch (MqttException disconnectFailure) {
          LOGGER.debug("Fleet MQTT disconnect failed", disconnectFailure);
        }
      }
    }
  }

  void receive(String topic, byte[] payload) {
    try {
      var observation = parse(topic, payload);
      telemetry.ingest(observation);
      lag.record(Duration.between(observation.recordedAt(), clock.instant()));
    } catch (IllegalArgumentException | tools.jackson.core.JacksonException
        | java.time.format.DateTimeParseException exception) {
      LOGGER.debug("Ignoring invalid fleet telemetry: {}", exception.getMessage());
    } catch (RuntimeException exception) {
      LOGGER.warn("Fleet telemetry transaction failed", exception);
    }
  }

  TelemetryObservation parse(String topic, byte[] payload) {
    var match = TOPIC.matcher(topic);
    if (!match.matches() || payload.length > 4096) {
      throw new IllegalArgumentException("Invalid telemetry topic or payload size");
    }
    var body = json.readTree(payload);
    if (body == null || !body.isObject()
        || !body.has("hubId")
        || !(body.get("hubId").isNull() || body.get("hubId").isTextual())
        || !body.path("batteryLevel").isIntegralNumber()
        || !body.path("batteryLevel").canConvertToInt()
        || !body.path("locked").isBoolean()
        || !body.path("criticalFault").isBoolean()
        || !body.path("recordedAt").isTextual()) {
      throw new IllegalArgumentException("Invalid telemetry message structure");
    }
    var hub = body.get("hubId");
    return new TelemetryObservation(
        match.group(1), hub.isNull() ? null : hub.asText(), body.get("batteryLevel").asInt(),
        body.get("locked").asBoolean(), body.get("criticalFault").asBoolean(),
        Instant.parse(body.get("recordedAt").asText()));
  }

  @TransactionalEventListener
  void sendLockCommand(VehicleLockCommand command) {
    if (!client.isConnected()) {
      LOGGER.warn("Fleet MQTT unavailable for simulated lock command on {}", command.kartId());
      return;
    }
    try {
      byte[] payload = Boolean.toString(command.locked()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      client.publish(TOPIC_PREFIX + command.kartId() + "/lock-command", payload, 1, true);
    } catch (MqttException exception) {
      LOGGER.warn("Could not publish simulated lock command on {}", command.kartId(), exception);
    }
  }

  @PreDestroy
  void close() throws MqttException {
    ready = false;
    if (client.isConnected()) {
      client.disconnectForcibly(100, 100);
    }
    client.close();
  }
}
