package Tech_Nagendra.Certificates_genration.Security;

import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Utility.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

@Component("auditorProvider")
public class AuditorAwareImpl implements AuditorAware<UserProfile> {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ProfileRepository profileRepository;

    @Override
    public Optional<UserProfile> getCurrentAuditor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserPrincipal userPrincipal) {
                    userId = userPrincipal.getId();
                }
                if (userId == null) {
                    String token = null;
                    if (authentication.getCredentials() != null) {
                        token = authentication.getCredentials().toString();
                    }
                    if (token == null) {
                        ServletRequestAttributes attributes =
                                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attributes != null) {
                            HttpServletRequest request = attributes.getRequest();
                            String header = request.getHeader("Authorization");
                            if (header != null && header.startsWith("Bearer ")) {
                                token = header.substring(7);
                            }
                        }
                    }


                    if (token != null && !token.isEmpty()) {
                        userId = jwtUtil.extractUserId(token);
                    }
                }
            }
            if (userId != null) {
                return profileRepository.findById(userId);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }
}
