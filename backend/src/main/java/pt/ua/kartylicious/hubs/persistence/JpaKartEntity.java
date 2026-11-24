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
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;

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

  void setStatus(KartStatus status) {
    this.status = status;
  }

  Kart toKart() {
    return new Kart(id, name, batteryLevel, status, active, criticalFault);
  }
}
