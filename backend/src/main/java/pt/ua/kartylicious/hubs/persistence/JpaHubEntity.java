package pt.ua.kartylicious.hubs.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import pt.ua.kartylicious.hubs.Hub;

@Entity
@Table(name = "hubs")
class JpaHubEntity {
  @Id private String id;
  private String name;
  private String address;

  @OneToMany(mappedBy = "hub", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<JpaKartEntity> karts = new ArrayList<>();

  protected JpaHubEntity() {}

  JpaHubEntity(Hub hub) {
    this.id = hub.id();
    this.name = hub.name();
    this.address = hub.address();
    this.karts =
        new ArrayList<>(hub.karts().stream().map(kart -> new JpaKartEntity(kart, this)).toList());
  }

  String getId() {
    return id;
  }

  String getName() {
    return name;
  }

  String getAddress() {
    return address;
  }

  List<JpaKartEntity> getKarts() {
    return karts;
  }

  Hub toHub() {
    return new Hub(id, name, address, karts.stream().map(JpaKartEntity::toKart).toList());
  }
}
