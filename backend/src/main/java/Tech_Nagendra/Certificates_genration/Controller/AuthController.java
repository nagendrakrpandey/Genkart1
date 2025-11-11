package Tech_Nagendra.Certificates_genration.Controller;

import Tech_Nagendra.Certificates_genration.Dto.AuthRequest;
import Tech_Nagendra.Certificates_genration.Dto.AuthResponse;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Service.ProfileService;
import Tech_Nagendra.Certificates_genration.Utility.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:8081", allowCredentials = "true")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpSession session) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserProfile userProfile = profileRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String token = jwtUtil.generateToken(
                    userProfile.getId(),
                    userProfile.getUsername(),
                    userProfile.getEmail(),
                    userProfile.getRole()
            );

            profileService.saveLoginToken(userProfile.getId(), token);
            session.setAttribute("JWT_TOKEN", token);

            Claims claims = jwtUtil.extractAllClaims(token);
            System.out.println("Generated JWT Claims -> userId: " + claims.get("userId") +
                    ", username: " + claims.getSubject() +
                    ", role: " + claims.get("role"));

            return ResponseEntity.ok(new AuthResponse(
                    true,
                    200,
                    "Login successful",
                    userProfile.getUsername(),
                    userProfile.getEmail(),
                    token
            ));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, 401, "Invalid username or password", null, null, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(false, 500, "Internal server error: " + e.getMessage(), null, null, null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(@RequestHeader(value = "Authorization", required = false) String tokenHeader,
                                               HttpSession session) {
        try {
            if (tokenHeader != null && tokenHeader.startsWith("Bearer ")) {
                String token = tokenHeader.substring(7).trim();
                Long userId = jwtUtil.extractUserId(token);

                if (userId != null) {
                    profileService.saveLoginToken(userId, null);
                    session.removeAttribute("JWT_TOKEN");
                } else {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(new AuthResponse(false, 401, "Invalid token", null, null, null));
                }
            }
            return ResponseEntity.ok(new AuthResponse(true, 200, "Logout successful", null, null, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(false, 500, "Logout failed: " + e.getMessage(), null, null, null));
        }
    }
}
