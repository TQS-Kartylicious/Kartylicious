package pt.ua.kartylicious.hubs;

import java.util.List;

public record Hub(String id, String name, String address, int capacity, List<Kart> karts) {
  public Hub {
    karts = List.copyOf(karts);
    if (capacity < 1) {
      throw new IllegalArgumentException("Hub capacity must be positive.");
    }
    var parkedKarts = karts.stream().filter(kart -> kart.status() != KartStatus.IN_USE).count();
    if (capacity < parkedKarts) {
      throw new IllegalArgumentException("Hub capacity cannot be below current occupancy.");
    }
  }
}
