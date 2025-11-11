package Tech_Nagendra.Certificates_genration.Controller;

import Tech_Nagendra.Certificates_genration.Dto.ProfileDto;
import Tech_Nagendra.Certificates_genration.Dto.ProfileStatsDto;
import Tech_Nagendra.Certificates_genration.Dto.UpdatePasswordDto;
import Tech_Nagendra.Certificates_genration.Service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:8081", allowCredentials = "true")
public class ProfileController {

    private final ProfileService profileService;

    private String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header must be provided in the format 'Bearer <token>'");
        }
        return header.substring(7).trim();
    }

    @GetMapping
    public ResponseEntity<?> getProfile(@RequestHeader(value = "Authorization", required = false) String tokenHeader) {
        try {
            String token = extractToken(tokenHeader);
            ProfileDto profile = profileService.getProfile(token);
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch profile: " + e.getMessage());
        }
    }


    @PutMapping
    public ResponseEntity<?> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @RequestBody ProfileDto profileDto) {
        try {
            String token = extractToken(tokenHeader);
            ProfileDto updatedProfile = profileService.updateProfile(token, profileDto);
            return ResponseEntity.ok(updatedProfile);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update profile: " + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @RequestBody ProfileDto profileDto,
            @RequestHeader(value = "Authorization", required = false) String tokenHeader) {
        try {
            String token = null;
            if (tokenHeader != null && tokenHeader.startsWith("Bearer ")) {
                token = extractToken(tokenHeader);
                System.out.println("Registration requested with JWT: " + token);
            }

            if (profileDto.getPassword() == null || profileDto.getPassword().isEmpty()) {
                return ResponseEntity.badRequest().body("Password is required.");
            }

            ProfileDto newUser = profileService.registerUser(profileDto, profileDto.getPassword());
            return ResponseEntity.status(HttpStatus.CREATED).body(newUser);

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to register user: " + e.getMessage());
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @RequestBody UpdatePasswordDto passwordDto) {
        try {
            String token = extractToken(tokenHeader);
            boolean updated = profileService.updatePassword(token, passwordDto);

            if (updated) {
                return ResponseEntity.ok("Password updated successfully");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Current password is incorrect");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update password: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(@RequestHeader(value = "Authorization", required = false) String tokenHeader) {
        try {
            String token = extractToken(tokenHeader);
            return ResponseEntity.ok(profileService.getAllUsers(token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch users: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestHeader(value = "Authorization", required = false) String tokenHeader) {
        try {
            String token = extractToken(tokenHeader);
            ProfileStatsDto stats = profileService.getStats(token);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch stats: " + e.getMessage());
        }
    }
}
