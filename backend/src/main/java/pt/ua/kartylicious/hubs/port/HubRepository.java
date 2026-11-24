package pt.ua.kartylicious.hubs.port;

import java.util.List;
import java.util.Optional;
import pt.ua.kartylicious.hubs.Hub;

public interface HubRepository {
  List<Hub> findAll();

  Optional<Hub> findById(String id);
}
