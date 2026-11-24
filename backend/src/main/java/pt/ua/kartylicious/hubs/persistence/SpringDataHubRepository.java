package pt.ua.kartylicious.hubs.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

interface SpringDataHubRepository extends JpaRepository<JpaHubEntity, String> {
  @Override
  @EntityGraph(attributePaths = "karts")
  List<JpaHubEntity> findAll();

  @Override
  @EntityGraph(attributePaths = "karts")
  Optional<JpaHubEntity> findById(String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select k from JpaKartEntity k where k.id = :kartId and k.hub.id = :hubId")
  Optional<JpaKartEntity> lockKart(
      @Param("hubId") String hubId, @Param("kartId") String kartId);
}
