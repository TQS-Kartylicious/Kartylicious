package pt.ua.kartylicious.hubs.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataHubRepository extends JpaRepository<JpaHubEntity, String> {
  @Override
  @EntityGraph(attributePaths = "karts")
  List<JpaHubEntity> findAll();

  @Override
  @EntityGraph(attributePaths = "karts")
  Optional<JpaHubEntity> findById(String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from JpaHubEntity h where h.id = :hubId")
  Optional<JpaHubEntity> lockHub(@Param("hubId") String hubId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select k from JpaKartEntity k where k.id = :kartId and k.hub.id = :hubId")
  Optional<JpaKartEntity> lockKart(
      @Param("hubId") String hubId, @Param("kartId") String kartId);

  @Query("select k from JpaKartEntity k where k.id = :kartId")
  Optional<JpaKartEntity> findKartById(@Param("kartId") String kartId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select k from JpaKartEntity k where k.id = :kartId")
  Optional<JpaKartEntity> lockKartById(@Param("kartId") String kartId);
}
