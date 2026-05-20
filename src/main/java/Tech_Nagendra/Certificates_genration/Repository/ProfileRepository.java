package Tech_Nagendra.Certificates_genration.Repository;

import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<UserProfile, Long> {

    // ================= USERNAME =================

    // 🔍 Find by username (case-insensitive)
    Optional<UserProfile> findByUsernameIgnoreCase(String username);

    // ✅ Check if username exists (case-insensitive)
    boolean existsByUsernameIgnoreCase(String username);


    // ================= EMAIL =================

    // 🔍 Find by email (case-insensitive)
    Optional<UserProfile> findByEmailIgnoreCase(String email);

    // ✅ Check if email exists (case-insensitive)
    boolean existsByEmailIgnoreCase(String email);


    // ================= ROLE =================

    // 👤 Get users by role (case-insensitive)
    List<UserProfile> findByRoleIgnoreCase(String role);


    // ================= STATUS (Optional - useful for dashboard stats) =================

    // 📊 Count users by status
    long countByStatus(int status);

}
