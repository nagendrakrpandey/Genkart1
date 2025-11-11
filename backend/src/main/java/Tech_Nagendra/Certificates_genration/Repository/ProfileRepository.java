package Tech_Nagendra.Certificates_genration.Repository;

import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

}

