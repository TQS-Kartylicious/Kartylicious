package pt.ua.kartylicious.hubs.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;

@Repository
public class JpaHubRepositoryAdapter implements HubRepository {
  private final SpringDataHubRepository repository;

  public JpaHubRepositoryAdapter(SpringDataHubRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<Hub> findAll() {
    return repository.findAll().stream().map(JpaHubEntity::toHub).toList();
  }

  @Override
  public Optional<Hub> findById(String id) {
    return repository.findById(id).map(JpaHubEntity::toHub);
  }

  @Override
  @Transactional
  public boolean reserveKart(String hubId, String kartId) {
    return repository.lockKart(hubId, kartId)
        .filter(
            kart ->
                (kart.getStatus() == KartStatus.AVAILABLE
                        || kart.getStatus() == KartStatus.RESERVED)
                    && kart.isActive()
                    && !kart.hasCriticalFault()
                    && kart.getBatteryLevel() >= 20)
        .map(
            kart -> {
              kart.setStatus(KartStatus.RESERVED);
              return true;
            })
        .orElse(false);
  }

  @Override
  @Transactional
  public boolean releaseKart(String hubId, String kartId) {
    return repository.lockKart(hubId, kartId)
        .filter(kart -> kart.getStatus() == KartStatus.RESERVED)
        .map(
            kart -> {
              kart.setStatus(KartStatus.AVAILABLE);
              return true;
            })
        .orElse(false);
  }
}
