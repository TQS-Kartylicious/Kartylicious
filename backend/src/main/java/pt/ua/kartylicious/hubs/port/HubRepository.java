package pt.ua.kartylicious.hubs.port;

import java.util.List;
import java.util.Optional;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;

public interface HubRepository {
  List<Hub> findAll();

  Optional<Hub> findById(String id);

  Optional<Kart> lockKart(String hubId, String kartId);

  boolean reserveKart(String hubId, String kartId);

  boolean releaseKart(String hubId, String kartId);

  boolean startKart(String hubId, String kartId, KartStatus expectedStatus);

  boolean markKartLowBattery(String hubId, String kartId);
}
