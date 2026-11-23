package pt.ua.kartylicious.hubs.seed;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;

@Repository
public class SeededHubRepository implements HubRepository {

  private static final List<Hub> HUBS =
      List.of(
          new Hub(
              "central-square",
              "Central Square",
              "Praça da República, 12",
              List.of(
                  new Kart("K-101", "Mini-kart 101", 84, KartStatus.AVAILABLE, true, false),
                  new Kart("K-102", "Mini-kart 102", 20, KartStatus.AVAILABLE, true, false),
                  new Kart("K-103", "Mini-kart 103", 19, KartStatus.LOW_BATTERY, true, false),
                  new Kart("K-104", "Mini-kart 104", 71, KartStatus.IN_USE, true, false),
                  new Kart("K-105", "Mini-kart 105", 63, KartStatus.RESERVED, true, false),
                  new Kart(
                      "K-106", "Mini-kart 106", 92, KartStatus.IN_MAINTENANCE, true, false))),
          new Hub(
              "riverside-park",
              "Riverside Park",
              "Avenida do Rio, 48",
              List.of(
                  new Kart("K-201", "Mini-kart 201", 18, KartStatus.LOW_BATTERY, true, false),
                  new Kart("K-202", "Mini-kart 202", 66, KartStatus.RESERVED, true, false))),
          new Hub(
              "university-gardens",
              "University Gardens",
              "Alameda da Universidade, 5",
              List.of(
                  new Kart("K-301", "Mini-kart 301", 56, KartStatus.AVAILABLE, true, false),
                  new Kart("K-302", "Mini-kart 302", 47, KartStatus.INACTIVE, false, false),
                  new Kart("K-303", "Mini-kart 303", 73, KartStatus.IN_MAINTENANCE, true, true))));

  @Override
  public List<Hub> findAll() {
    return HUBS;
  }

  @Override
  public Optional<Hub> findById(String id) {
    return HUBS.stream().filter(hub -> hub.id().equals(id)).findFirst();
  }
}
