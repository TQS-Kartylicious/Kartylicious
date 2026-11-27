package pt.ua.kartylicious.hubs;

import java.util.List;

public record Hub(String id, String name, String address, int capacity, List<Kart> karts) {
  public Hub {
    karts = List.copyOf(karts);
    if (capacity < 1) {
      throw new IllegalArgumentException("Hub capacity must be positive.");
    }
    if (capacity < occupancy(karts)) {
      throw new IllegalArgumentException("Hub capacity cannot be below current occupancy.");
    }
  }

  public long occupancy() {
    return occupancy(karts);
  }

  private static long occupancy(List<Kart> karts) {
    return karts.stream().filter(kart -> kart.status() != KartStatus.IN_USE).count();
  }
}
