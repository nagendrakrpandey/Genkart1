package Tech_Nagendra.Certificates_genration.Repository;


import Tech_Nagendra.Certificates_genration.Entity.UserLoginSession;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserLoginSessionRepository extends JpaRepository<UserLoginSession, Long> {

    List<UserLoginSession> findByUserAndActive(UserProfile user, boolean active);

    Optional<UserLoginSession> findByJwtToken(String token);
}

