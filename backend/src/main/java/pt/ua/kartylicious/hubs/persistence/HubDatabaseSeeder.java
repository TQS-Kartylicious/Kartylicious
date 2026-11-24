package pt.ua.kartylicious.hubs.persistence;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;

@Component
public class HubDatabaseSeeder implements CommandLineRunner {
  private final SpringDataHubRepository repository;

  public HubDatabaseSeeder(SpringDataHubRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (repository.count() != 0) {
      return;
    }
    repository.saveAll(
        List.of(
            hub(
                "central-square",
                "Central Square",
                "Praça da República, 12",
                new Kart("K-101", "Mini-kart 101", 84, KartStatus.AVAILABLE, true, false),
                new Kart("K-102", "Mini-kart 102", 20, KartStatus.AVAILABLE, true, false),
                new Kart("K-103", "Mini-kart 103", 19, KartStatus.LOW_BATTERY, true, false),
                new Kart("K-104", "Mini-kart 104", 71, KartStatus.IN_USE, true, false),
                new Kart("K-105", "Mini-kart 105", 63, KartStatus.RESERVED, true, false),
                new Kart("K-106", "Mini-kart 106", 92, KartStatus.IN_MAINTENANCE, true, false)),
            hub(
                "riverside-park",
                "Riverside Park",
                "Avenida do Rio, 48",
                new Kart("K-201", "Mini-kart 201", 18, KartStatus.LOW_BATTERY, true, false),
                new Kart("K-202", "Mini-kart 202", 66, KartStatus.RESERVED, true, false)),
            hub(
                "university-gardens",
                "University Gardens",
                "Alameda da Universidade, 5",
                new Kart("K-301", "Mini-kart 301", 56, KartStatus.AVAILABLE, true, false),
                new Kart("K-302", "Mini-kart 302", 47, KartStatus.INACTIVE, false, false),
                new Kart("K-303", "Mini-kart 303", 73, KartStatus.IN_MAINTENANCE, true, true)))
        .stream()
        .map(JpaHubEntity::new)
        .toList());
  }

  private Hub hub(String id, String name, String address, Kart... karts) {
    return new Hub(id, name, address, List.of(karts));
  }
}
