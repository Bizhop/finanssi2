package fi.bizhop.finanssi2.db;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationUserRepository extends JpaRepository<ApplicationUser, UUID> {
    Optional<ApplicationUser> findByFirebaseUid(String firebaseUid);
    Optional<ApplicationUser> findByEmail(String email);
}
