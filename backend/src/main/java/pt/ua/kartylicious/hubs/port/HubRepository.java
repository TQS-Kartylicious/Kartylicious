package pt.ua.kartylicious.hubs.port;

import java.util.List;
import java.util.Optional;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.TelemetryObservation;

public interface HubRepository {
  List<Hub> findAll();

  Optional<Hub> findById(String id);

  boolean hubExists(String id);

  Optional<Hub> lockHub(String hubId);

  void insertHub(Hub hub);

  /** The caller holds the hub lock. */
  void updateHub(Hub hub);

  boolean kartExists(String kartId);

  /** The caller holds the hub lock. */
  void insertKart(String hubId, Kart kart);

  Optional<Kart> lockKart(String hubId, String kartId);

  Optional<Kart> lockKartById(String kartId);

  /** The caller holds the kart lock. */
  void updateKartAvailability(String kartId, boolean active, KartStatus status);

  boolean reserveKart(String hubId, String kartId);

  boolean reconcileReservedKart(String kartId, boolean hasReservation);

  boolean reportCriticalFault(String kartId);

  /** The caller holds the kart lock. Returns true when this observation opens a critical fault. */
  boolean recordTelemetry(TelemetryObservation observation, boolean hasReservation);

  boolean startKart(String hubId, String kartId, KartStatus expectedStatus);

  boolean markKartLowBattery(String hubId, String kartId);

  boolean parkKartAtHub(String hubId, String kartId, KartStatus status);
}
