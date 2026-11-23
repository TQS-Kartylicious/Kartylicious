package pt.ua.kartylicious.hubs;

import java.util.List;

public record Hub(String id, String name, String address, List<Kart> karts) {
  public Hub {
    karts = List.copyOf(karts);
  }
}
