
package Tech_Nagendra.Certificates_genration.Controller;
import Tech_Nagendra.Certificates_genration.Dto.ProfileDto;
import Tech_Nagendra.Certificates_genration.Dto.ProfileStatsDto;
import Tech_Nagendra.Certificates_genration.Dto.UpdatePasswordDto;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    // ================= TOKEN EXTRACT =================

    private String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException(
                    "Authorization header must be provided in the format 'Bearer <token>'"
            );
        }
        return header.substring(7).trim();
    }

    // ================= PROFILE =================

    @GetMapping
    public ResponseEntity<?> getProfile(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader) {

        try {
            String token = extractToken(tokenHeader);
            return ResponseEntity.ok(profileService.getProfile(token));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @RequestBody ProfileDto profileDto) {

        try {
            String token = extractToken(tokenHeader);
            return ResponseEntity.ok(profileService.updateProfile(token, profileDto));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    // ================= REGISTER =================

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody ProfileDto profileDto) {

        try {
            if (profileDto.getPassword() == null || profileDto.getPassword().isEmpty()) {
                return ResponseEntity.badRequest().body("Password is required");
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(profileService.registerUser(profileDto, profileDto.getPassword()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // ================= PASSWORD =================

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @RequestBody UpdatePasswordDto passwordDto) {

        try {
            String token = extractToken(tokenHeader);
            boolean updated = profileService.updatePassword(token, passwordDto);

            return updated
                    ? ResponseEntity.ok("Password updated successfully")
                    : ResponseEntity.badRequest().body("Current password is incorrect");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    // ================= STATS =================

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader) {

        try {
            String token = extractToken(tokenHeader);
            ProfileStatsDto stats = profileService.getStats(token);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    // ================= PAYMENTS (SELECTED USER) =================

    // ➕ ADD payment to selected user
    @PostMapping("/payment/{userId}")
    public ResponseEntity<String> addPaymentForUser(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @PathVariable Long userId,
            @RequestParam BigDecimal amount) {

        String token = extractToken(tokenHeader);
        profileService.addPaymentForUser(token, userId, amount);
        return ResponseEntity.ok("Payment added successfully");
    }

    // ✏️ UPDATE (overwrite) payment
    @PutMapping("/update/payment/{userId}")
    public ResponseEntity<String> updatePaymentForUser(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @PathVariable Long userId,
            @RequestParam BigDecimal amount) {

        String token = extractToken(tokenHeader);
        profileService.updatePaymentForUser(token, userId, amount);
        return ResponseEntity.ok("Payment updated successfully");
    }

    @PutMapping("/clear/payment/{userId}")
    public ResponseEntity<String> clearPayment(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @PathVariable Long userId) {

        String token = extractToken(tokenHeader);
        profileService.clearPaymentForUser(token, userId);

        return ResponseEntity.ok("Payment cleared successfully");
    }


    // 📥 GET payment of selected user
    @GetMapping("/get/payment/{userId}")
    public ResponseEntity<BigDecimal> getPaymentForUser(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @PathVariable Long userId) {

        String token = extractToken(tokenHeader);
        return ResponseEntity.ok(
                profileService.getPaymentForUser(token, userId)
        );
    }

    // ================= ADMIN =================

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader) {

        try {
            String token = extractToken(tokenHeader);
            return ResponseEntity.ok(profileService.getAllUsers(token));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @GetMapping("/users-only")
    public ResponseEntity<List<Map<String, Object>>> getOnlyUsers(
            @RequestHeader("Authorization") String token
    ) {
        return ResponseEntity.ok(profileService.getOnlyUsers(token));
    }


    @PutMapping("/status/{userId}")
    public ResponseEntity<?> updateUserStatus(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @PathVariable Long userId,
            @RequestParam Integer status) {

        try {
            String token = extractToken(tokenHeader);
            ProfileDto admin = profileService.getProfile(token);

            if (!"ADMIN".equalsIgnoreCase(admin.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Only admin can update user status");
            }

            UserProfile user = profileService.findById(userId);
            user.setStatus(status);
            profileService.save(user);

            return ResponseEntity.ok("User status updated successfully");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
}
