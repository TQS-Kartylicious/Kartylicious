package pt.ua.kartylicious.hubs.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.TelemetryObservation;
import pt.ua.kartylicious.hubs.port.HubRepository;

@Repository
public class JpaHubRepositoryAdapter implements HubRepository {
  private final SpringDataHubRepository repository;

  public JpaHubRepositoryAdapter(SpringDataHubRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<Hub> findAll() {
    return repository.findAll().stream().map(JpaHubEntity::toHub).toList();
  }

  @Override
  public Optional<Hub> findById(String id) {
    return repository.findById(id).map(JpaHubEntity::toHub);
  }

  @Override
  public boolean hubExists(String id) {
    return repository.existsById(id);
  }

  @Override
  @Transactional
  public Optional<Hub> lockHub(String hubId) {
    return repository.lockHub(hubId).map(JpaHubEntity::toHub);
  }

  @Override
  @Transactional
  public Optional<Kart> lockKart(String hubId, String kartId) {
    return repository.lockKart(hubId, kartId).map(JpaKartEntity::toKart);
  }

  @Override
  @Transactional
  public Optional<Kart> lockKartById(String kartId) {
    return repository.lockKartById(kartId).map(JpaKartEntity::toKart);
  }

  @Override
  @Transactional
  public boolean reserveKart(String hubId, String kartId) {
    return repository.lockKart(hubId, kartId)
        .filter(
            kart ->
                (kart.getStatus() == KartStatus.AVAILABLE
                        || kart.getStatus() == KartStatus.RESERVED)
                    && kart.isActive()
                    && !kart.hasCriticalFault()
                    && kart.getBatteryLevel() >= 20)
        .map(
            kart -> {
              kart.setStatus(KartStatus.RESERVED);
              return true;
            })
        .orElse(false);
  }

  @Override
  @Transactional
  public boolean reconcileReservedKart(String kartId, boolean hasReservation) {
    return repository.lockKartById(kartId)
        .filter(kart -> kart.getStatus() == KartStatus.RESERVED)
        .map(
            kart -> {
              kart.reconcileReservation(hasReservation);
              return true;
            })
        .orElse(false);
  }

  @Override
  @Transactional
  public boolean reportCriticalFault(String kartId) {
    var kart = repository.lockKartById(kartId);
    kart.ifPresent(JpaKartEntity::flagCriticalFault);
    return kart.isPresent();
  }

  @Override
  public boolean recordTelemetry(TelemetryObservation observation, boolean hasReservation) {
    return repository.findKartById(observation.kartId())
        .map(kart -> kart.applyTelemetry(observation, hasReservation))
        .orElse(false);
  }

  @Override
  @Transactional
  public boolean startKart(String hubId, String kartId, KartStatus expectedStatus) {
    return repository.lockKart(hubId, kartId)
        .filter(
            kart ->
                kart.getStatus() == expectedStatus
                    && kart.isActive()
                    && !kart.hasCriticalFault()
                    && kart.getBatteryLevel() >= 20)
        .map(
            kart -> {
              kart.setStatus(KartStatus.IN_USE);
              kart.setLocked(false);
              return true;
            })
        .orElse(false);
  }

  @Override
  @Transactional
  public boolean markKartLowBattery(String hubId, String kartId) {
    return repository.lockKart(hubId, kartId)
        .filter(
            kart ->
                (kart.getStatus() == KartStatus.AVAILABLE || kart.getStatus() == KartStatus.RESERVED)
                    && kart.isActive()
                    && !kart.hasCriticalFault()
                    && kart.getBatteryLevel() < 20)
        .map(
            kart -> {
              kart.setStatus(KartStatus.LOW_BATTERY);
              return true;
            })
        .orElse(false);
  }

  @Override
  @Transactional
  public boolean parkKartAtHub(String hubId, String kartId, KartStatus status) {
    var hub = repository.lockHub(hubId);
    var kart = repository.lockKartById(kartId);
    if (hub.isEmpty() || kart.isEmpty() || kart.get().getStatus() != KartStatus.IN_USE) {
      return false;
    }
    kart.get().setHub(hub.get());
    kart.get().setStatus(status);
    return true;
  }
}
