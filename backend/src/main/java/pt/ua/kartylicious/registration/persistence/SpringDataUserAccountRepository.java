package pt.ua.kartylicious.registration.persistence;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;

interface SpringDataUserAccountRepository extends JpaRepository<JpaUserAccountEntity, UUID> {

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  Optional<JpaUserAccountEntity> findByEmail(String email);

  List<JpaUserAccountEntity> findAllByOrderByUsernameAsc();

  long countByRoleAndActive(pt.ua.kartylicious.registration.UserRole role, boolean active);

  long countByRole(pt.ua.kartylicious.registration.UserRole role);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from JpaUserAccountEntity u where u.role = "
      + "pt.ua.kartylicious.registration.UserRole.ADMIN and u.active = true")
  List<JpaUserAccountEntity> lockActiveAdmins();
}
