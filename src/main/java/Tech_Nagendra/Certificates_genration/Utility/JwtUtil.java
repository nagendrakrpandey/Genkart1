package Tech_Nagendra.Certificates_genration.Utility;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecretBase64;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecretBase64);
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JWT secret key", e);
        }
    }

    public String generateToken(Long userId, String username, String email, String role) {
        if (userId == null || username == null) {
            throw new IllegalArgumentException("userId and username cannot be null for JWT generation");
        }

        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("email", email != null ? email : "")
                .claim("role", role != null ? role : "")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 7)) // 7 days
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        token = cleanToken(token);
        if (token.isEmpty()) throw new IllegalArgumentException("JWT token is missing");
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid JWT token: " + e.getMessage());
        }
    }

    public String cleanToken(String token) {
        if (token == null) return "";
        token = token.trim();
        if (token.startsWith("Bearer ")) token = token.substring(7);
        return token.replaceAll("[^\\x20-\\x7E]", "");
    }

    public Long extractUserId(String token) {
        Object userIdObj = extractAllClaims(token).get("userId");
        if (userIdObj == null) throw new IllegalArgumentException("JWT token missing 'userId' claim");
        if (userIdObj instanceof Integer) return ((Integer) userIdObj).longValue();
        if (userIdObj instanceof Long) return (Long) userIdObj;
        return Long.parseLong(userIdObj.toString());
    }

    public String extractUsername(String token) {
        String username = extractAllClaims(token).getSubject();
        if (username == null) throw new IllegalArgumentException("JWT token missing 'username' (subject)");
        return username;
    }

    public String extractEmail(String token) {
        Object email = extractAllClaims(token).get("email");
        return email != null ? email.toString() : "";
    }

    public String extractRole(String token) {
        Object role = extractAllClaims(token).get("role");
        return role != null ? role.toString() : "";
    }

    public boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    public boolean validateToken(String token, String username) {
        try {
            String extractedUsername = extractUsername(token);
            return extractedUsername.equals(username) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
