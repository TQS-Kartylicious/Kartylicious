package pt.ua.kartylicious.hubs.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.TelemetryObservation;

@Entity
@Table(name = "karts")
class JpaKartEntity {
  @Id private String id;
  private String name;

  @Column(name = "battery_level", nullable = false)
  private int batteryLevel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private KartStatus status;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "critical_fault", nullable = false)
  private boolean criticalFault;

  @Column(name = "telemetry_hub_id", length = 64)
  private String telemetryHubId;

  @Column(name = "telemetry_recorded_at")
  private Instant telemetryRecordedAt;

  private Boolean locked;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hub_id", nullable = false)
  private JpaHubEntity hub;

  protected JpaKartEntity() {}

  JpaKartEntity(Kart kart, JpaHubEntity hub) {
    this.id = kart.id();
    this.name = kart.name();
    this.batteryLevel = kart.batteryLevel();
    this.status = kart.status();
    this.active = kart.active();
    this.criticalFault = kart.criticalFault();
    this.hub = hub;
    this.locked = true;
  }

  String getId() {
    return id;
  }

  String getName() {
    return name;
  }

  int getBatteryLevel() {
    return batteryLevel;
  }

  KartStatus getStatus() {
    return status;
  }

  boolean isActive() {
    return active;
  }

  boolean hasCriticalFault() {
    return criticalFault;
  }

  void flagCriticalFault() {
    criticalFault = true;
    if (active
        && (status == KartStatus.AVAILABLE
            || status == KartStatus.RESERVED
            || status == KartStatus.LOW_BATTERY)) {
      status = KartStatus.IN_MAINTENANCE;
    }
  }

  boolean applyTelemetry(TelemetryObservation reading, boolean hasReservation) {
    if (telemetryRecordedAt != null && !reading.recordedAt().isAfter(telemetryRecordedAt)) {
      return false;
    }
    telemetryHubId = reading.hubId();
    telemetryRecordedAt = reading.recordedAt();
    batteryLevel = reading.batteryLevel();
    locked = reading.locked();
    boolean newFault = reading.criticalFault() && !criticalFault;
    if (reading.criticalFault()) {
      flagCriticalFault();
    }
    if (active && !criticalFault) {
      if (batteryLevel < 20 && (status == KartStatus.AVAILABLE || status == KartStatus.RESERVED)) {
        status = KartStatus.LOW_BATTERY;
      } else if (batteryLevel >= 20 && status == KartStatus.LOW_BATTERY) {
        status = hasReservation ? KartStatus.RESERVED : KartStatus.AVAILABLE;
      }
    }
    return newFault;
  }

  void reconcileReservation(boolean hasReservation) {
    if (!active) {
      status = KartStatus.INACTIVE;
    } else if (criticalFault) {
      status = KartStatus.IN_MAINTENANCE;
    } else if (batteryLevel < 20) {
      status = KartStatus.LOW_BATTERY;
    } else if (!hasReservation) {
      status = KartStatus.AVAILABLE;
    }
  }

  String getTelemetryHubId() {
    return telemetryHubId;
  }

  Instant getTelemetryRecordedAt() {
    return telemetryRecordedAt;
  }

  void setStatus(KartStatus status) {
    this.status = status;
  }

  void setHub(JpaHubEntity hub) {
    this.hub = hub;
  }

  void setLocked(boolean locked) {
    this.locked = locked;
  }

  Kart toKart() {
    return new Kart(id, name, batteryLevel, status, active, criticalFault);
  }
}
