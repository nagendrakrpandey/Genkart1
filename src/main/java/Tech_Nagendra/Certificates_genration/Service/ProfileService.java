
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ReportRepository reportRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // ================= SAVE LOGIN TOKEN =================
    public void saveLoginToken(Long userId, String token) {

        UserProfile user = findById(userId);
        user.setLoginToken(token);
        user.setModifiedAt(LocalDateTime.now());

        profileRepository.save(user);
    }

    // ================= TOKEN VALIDATION =================

    private Long extractAndValidateToken(String authHeader) {

        if (authHeader == null || authHeader.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization token required"
            );
        }

        // ✅ Remove "Bearer " prefix if present
        String token = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;

        try {
            Long userId = jwtUtil.extractUserId(token);
            UserProfile user = findById(userId);

            // ✅ ADMIN ke liye loginToken validation SKIP
            if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
                if (user.getLoginToken() == null || !user.getLoginToken().equals(token)) {
                    throw new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "Invalid or expired token"
                    );
                }
            }

            return userId;

        } catch (ExpiredJwtException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token expired"
            );
        } catch (MalformedJwtException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token"
            );
        }
    }


    // ================= REGISTER USER ========================

//    public ProfileDto registerUser(ProfileDto dto, String rawPassword) {
//
//        if (profileRepository.existsByEmail(dto.getEmail()))
//            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
//
//        if (profileRepository.existsByUsername(dto.getUsername()))
//            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
//
//        UserProfile user = new UserProfile();
//        user.setName(dto.getName());
//        user.setUsername(dto.getUsername());
//        user.setEmail(dto.getEmail());
//        user.setPassword(passwordEncoder.encode(rawPassword));
//        user.setRole(dto.getRole() != null ? dto.getRole() : "USER");
//        user.setStatus(0);
//        user.setTotalPaymentAmount(BigDecimal.ZERO);
//        user.setCreatedAt(LocalDateTime.now());
//        user.setModifiedAt(LocalDateTime.now());
//
//        return mapToDto(profileRepository.save(user));
//    }



    // ================= REGISTER USER ========================

    public ProfileDto registerUser(ProfileDto dto, String rawPassword) {

        if (dto.getUsername() == null || dto.getUsername().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");

        if (dto.getEmail() == null || dto.getEmail().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");

        // ✅ Trim + Lowercase for consistency
        String username = dto.getUsername().trim().toLowerCase();
        String email = dto.getEmail().trim().toLowerCase();

        // ✅ Case-insensitive duplicate check
        if (profileRepository.existsByUsernameIgnoreCase(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");

        if (profileRepository.existsByEmailIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");

        UserProfile user = new UserProfile();
        user.setName(dto.getName() != null ? dto.getName().trim() : null);
        user.setUsername(username);  // 🔥 Always lowercase store
        user.setEmail(email);        // 🔥 Always lowercase store
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(dto.getRole() != null ? dto.getRole().toUpperCase() : "USER");
        user.setStatus(0);
        user.setTotalPaymentAmount(BigDecimal.ZERO);
        user.setCreatedAt(LocalDateTime.now());
        user.setModifiedAt(LocalDateTime.now());

        return mapToDto(profileRepository.save(user));
    }



    // ================= GET PROFILE (TOKEN BASED) =================

    public ProfileDto getProfile(String token) {
        Long userId = extractAndValidateToken(token);
        return mapToDto(findById(userId));
    }

    // ================= UPDATE PROFILE =================

    public ProfileDto updateProfile(String token, ProfileDto dto) {

        Long userId = extractAndValidateToken(token);
        UserProfile user = findById(userId);

        if (dto.getName() != null && !dto.getName().isBlank())
            user.setName(dto.getName());

        if (dto.getUsername() != null && !dto.getUsername().isBlank())
            user.setUsername(dto.getUsername());

        if (dto.getEmail() != null && !dto.getEmail().isBlank())
            user.setEmail(dto.getEmail());

        user.setModifiedAt(LocalDateTime.now());
        return mapToDto(profileRepository.save(user));
    }

    // ================= UPDATE PASSWORD =================

    public boolean updatePassword(String token, UpdatePasswordDto dto) {

        Long userId = extractAndValidateToken(token);
        UserProfile user = findById(userId);

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setModifiedAt(LocalDateTime.now());
        profileRepository.save(user);
        return true;
    }

    // ================= USER STATS =================

    public ProfileStatsDto getStats(String token) {

        Long userId = extractAndValidateToken(token);

        long totalCertificates =
                reportRepository.countByGeneratedBy_Id(userId);

        long activeCertificates =
                reportRepository.countByGeneratedBy_IdAndStatus(userId, "ACTIVE");

        Optional<Report> lastReport =
                reportRepository.findTopByGeneratedBy_IdOrderByGeneratedOnDesc(userId);

        String lastGenerated =
                lastReport.map(r -> r.getGeneratedOn().toString()).orElse("N/A");

        return new ProfileStatsDto(
                (int) totalCertificates,
                (int) activeCertificates,
                lastGenerated
        );
    }

    // ================= FIND USER =================

    public UserProfile findById(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found with ID: " + userId
                        )
                );
    }

    // ================= PAYMENTS (SELECTED USER) =================

    // ➕ Add amount (increment)
    @Transactional
    public void addPaymentForUser(String token, Long targetUserId, BigDecimal amount) {

        extractAndValidateToken(token);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be > 0");
        }

        UserProfile user = findById(targetUserId);

        if (user.getTotalPaymentAmount() == null)
            user.setTotalPaymentAmount(BigDecimal.ZERO);

        user.setTotalPaymentAmount(user.getTotalPaymentAmount().add(amount));
        user.setModifiedAt(LocalDateTime.now());

        profileRepository.save(user);
    }

    // ✏️ Update / overwrite amount
    @Transactional
    public void updatePaymentForUser(String token, Long targetUserId, BigDecimal amount) {

        extractAndValidateToken(token);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount cannot be negative");
        }

        UserProfile user = findById(targetUserId);
        user.setTotalPaymentAmount(amount);
        user.setModifiedAt(LocalDateTime.now());

        profileRepository.save(user);
    }


    @Transactional
    public void clearPaymentForUser(String token, Long userId) {

        extractAndValidateToken(token); // token valid check

        UserProfile user = findById(userId);

        // ✅ payment clear = ZERO (not null)
        user.setTotalPaymentAmount(BigDecimal.ZERO);

        user.setModifiedAt(LocalDateTime.now());
        profileRepository.save(user);
    }


    // 📥 Get payment
    public BigDecimal getPaymentForUser(String token, Long targetUserId) {

        extractAndValidateToken(token);

        return findById(targetUserId).getTotalPaymentAmount();
    }

    // ================= GET ALL USERS (ADMIN) =================

    public List<Map<String, Object>> getAllUsers(String token) {

        extractAndValidateToken(token);

        return profileRepository.findAll().stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("email", u.getEmail());
            map.put("username", u.getUsername());
            map.put("status", u.getStatus());
            map.put("payment", u.getTotalPaymentAmount());
            return map;
        }).toList();
    }

    // ================= SAVE =================

    public UserProfile save(UserProfile userProfile) {
        return profileRepository.save(userProfile);
    }

    //============only user not admin=============

    // ================= GET ONLY USERS (NO ADMIN) =================
    public List<Map<String, Object>> getOnlyUsers(String token) {

        extractAndValidateToken(token); // ✅ token validation

        return profileRepository.findByRoleIgnoreCase("USER")
                .stream()
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("name", u.getName());
                    map.put("username", u.getUsername());
                    map.put("email", u.getEmail());
                    map.put("status", u.getStatus());
                    map.put("payment", u.getTotalPaymentAmount());
                    return map;
                })
                .toList();
    }

    // ================= DTO MAPPER =================

    private ProfileDto mapToDto(UserProfile user) {

        ProfileDto dto = new ProfileDto(
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
        dto.setStatus(user.getStatus());
        return dto;
    }
}
