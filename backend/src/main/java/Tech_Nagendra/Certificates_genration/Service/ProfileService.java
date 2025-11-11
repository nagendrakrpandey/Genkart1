package Tech_Nagendra.Certificates_genration.Service;

import Tech_Nagendra.Certificates_genration.Dto.ProfileDto;
import Tech_Nagendra.Certificates_genration.Dto.ProfileStatsDto;
import Tech_Nagendra.Certificates_genration.Dto.UpdatePasswordDto;
import Tech_Nagendra.Certificates_genration.Entity.Report;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Repository.ReportRepository;
import Tech_Nagendra.Certificates_genration.Utility.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ReportRepository reportRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public ProfileDto registerUser(ProfileDto profileDto, String rawPassword) {
        if (profileDto.getEmail() == null || profileDto.getUsername() == null || rawPassword == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email, Username and Password are required");
        }

        if (profileRepository.existsByEmail(profileDto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        if (profileRepository.existsByUsername(profileDto.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        UserProfile newUser = new UserProfile();
        newUser.setName(profileDto.getName());
        newUser.setUsername(profileDto.getUsername());
        newUser.setEmail(profileDto.getEmail());
        newUser.setPassword(passwordEncoder.encode(rawPassword));
        newUser.setRole(profileDto.getRole() != null ? profileDto.getRole() : "USER");
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setModifiedAt(LocalDateTime.now());
        newUser.setCreatedBy(null);
        newUser.setModifiedBy(null);

        UserProfile savedUser = profileRepository.save(newUser);
        return mapToDto(savedUser);
    }

    public ProfileDto getProfile(String token) {
        Long currentUserId = extractAndValidateToken(token);
        UserProfile user = findById(currentUserId);
        return mapToDto(user);
    }

    public ProfileDto updateProfile(String token, ProfileDto profileDto) {
        Long currentUserId = extractAndValidateToken(token);
        UserProfile currentUser = findById(currentUserId);
        UserProfile targetUser = currentUser;

        if (profileDto.getName() != null && !profileDto.getName().isBlank()) {
            targetUser.setName(profileDto.getName());
        }
        if (profileDto.getUsername() != null && !profileDto.getUsername().isBlank()) {
            targetUser.setUsername(profileDto.getUsername());
        }
        if (profileDto.getEmail() != null && !profileDto.getEmail().isBlank()) {
            targetUser.setEmail(profileDto.getEmail());
        }

        targetUser.setModifiedBy(currentUser);
        targetUser.setModifiedAt(LocalDateTime.now());

        UserProfile updated = profileRepository.save(targetUser);
        return mapToDto(updated);
    }

    public boolean updatePassword(String token, UpdatePasswordDto dto) {
        Long currentUserId = extractAndValidateToken(token);
        UserProfile user = findById(currentUserId);

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setModifiedBy(user);
        user.setModifiedAt(LocalDateTime.now());
        profileRepository.save(user);
        return true;
    }

    public ProfileStatsDto getStats(String token) {
        Long currentUserId = extractAndValidateToken(token);
        findById(currentUserId);

        long totalCertificates = reportRepository.countByGeneratedBy_Id(currentUserId);
        long activeCertificates = reportRepository.countByGeneratedBy_IdAndStatus(currentUserId, "ACTIVE");
        Optional<Report> lastReport = reportRepository.findTopByGeneratedBy_IdOrderByGeneratedOnDesc(currentUserId);
        String lastGenerated = lastReport.map(r -> r.getGeneratedOn().toString()).orElse("N/A");

        return new ProfileStatsDto((int) totalCertificates, (int) activeCertificates, lastGenerated);
    }

    public UserProfile findById(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId));
    }


    public List<Map<String, Object>> getAllUsers(String token) {
        extractAndValidateToken(token);
        try {
            return profileRepository.findAll().stream()
                    .map(u -> {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", u.getId());
                        userMap.put("name", u.getName());
                        userMap.put("email", u.getEmail());
                        return userMap;
                    })
                    .toList();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch user list");
        }
    }

    public void saveLoginToken(Long userId, String token) {
        UserProfile user = findById(userId);
        user.setLoginToken(token);
        profileRepository.save(user);
    }


    private Long extractAndValidateToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization token required");
        }

        try {
            Long userId = jwtUtil.extractUserId(token);
            UserProfile user = findById(userId);

            if (user.getLoginToken() == null || !user.getLoginToken().equals(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }
            return userId;
        } catch (ExpiredJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
        } catch (MalformedJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token format");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token validation failed: " + e.getMessage());
        }
    }


    private ProfileDto mapToDto(UserProfile user) {
        return new ProfileDto(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getModifiedAt(),
                user.getCreatedBy() != null ? user.getCreatedBy().getId() : null,
                user.getModifiedBy() != null ? user.getModifiedBy().getId() : null
        );
    }
}
