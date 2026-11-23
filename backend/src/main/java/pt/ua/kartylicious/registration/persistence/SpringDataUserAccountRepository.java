package pt.ua.kartylicious.registration.persistence;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserAccountRepository extends JpaRepository<JpaUserAccountEntity, UUID> {

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  Optional<JpaUserAccountEntity> findByEmail(String email);
}
