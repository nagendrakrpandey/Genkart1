package Tech_Nagendra.Certificates_genration.JWTfilter;

import Tech_Nagendra.Certificates_genration.Security.UserPrincipal;
import Tech_Nagendra.Certificates_genration.Utility.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        String jwt = null;

        System.out.println(">> Incoming request: " + request.getMethod() + " " + request.getRequestURI());
        System.out.println("   Authorization header: " + (authHeader == null ? "null" : authHeader));

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7).trim();
            jwt = jwt.replaceAll("[<>]", "");
        }

        if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                jwt = jwtUtil.cleanToken(jwt);
                System.out.println("   Cleaned token (first 40 chars): " +
                        (jwt.length() > 40 ? jwt.substring(0, 40) + "..." : jwt));

                Long userId = jwtUtil.extractUserId(jwt);
                String username = jwtUtil.extractUsername(jwt);
                String role = jwtUtil.extractRole(jwt);

                System.out.println("   Parsed claims -> userId: " + userId + ", username: " + username + ", role: " + role);

                if (userId != null && username != null) {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (role != null && !role.isEmpty()) {
                        for (String r : role.split(",")) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + r.trim().toUpperCase()));
                        }
                    }

                    UserPrincipal principal = new UserPrincipal(userId, username, "", role, authorities);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authToken);

                } else {
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing required JWT claims (userId/username)");
                    return;
                }

            } catch (ExpiredJwtException e) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT expired. Please login again.");
                return;
            } catch (MalformedJwtException e) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Malformed JWT token");
                return;
            } catch (SignatureException e) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT signature");
                return;
            } catch (IllegalArgumentException e) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT token is missing or empty");
                return;
            } catch (Exception e) {
                e.printStackTrace();
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
                return;
            }
        } else if (jwt == null) {
            System.out.println("   No JWT token provided in Authorization header.");
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setContentType("application/json");
        response.setStatus(status);
        response.getWriter().write(String.format("{\"error\":\"%s\"}", message));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/public")
                || path.startsWith("/login")
                || path.startsWith("/register")
                || path.startsWith("/auth")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}
