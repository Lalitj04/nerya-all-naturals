package com.nerya.neryaallnaturals.util;

import com.nerya.neryaallnaturals.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:mySecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLongForHS256Algorithm}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // Default: 24 hours in milliseconds
    private Long expiration;

    /**
     * Generate JWT token for authenticated user
     *
     * @param user the authenticated user
     * @return JWT token string
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("roles", user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toList()));

        return createToken(claims, user.getUsername());
    }

    /**
     * Create JWT token with claims
     *
     * @param claims additional claims to include in token
     * @param subject the subject (username)
     * @return JWT token string
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Get signing key from secret
     *
     * @return SecretKey for signing JWT
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extract username from JWT token
     *
     * @param token JWT token
     * @return username
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extract email from JWT token
     *
     * @param token JWT token
     * @return email
     */
    public String extractEmail(String token) {
        return (String) extractAllClaims(token).get("email");
    }

    /**
     * Extract roles from JWT token
     *
     * @param token JWT token
     * @return set of roles
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(String token) {
        return Set.copyOf((java.util.List<String>) extractAllClaims(token).get("roles"));
    }

    /**
     * Extract expiration date from JWT token
     *
     * @param token JWT token
     * @return expiration date
     */
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /**
     * Extract all claims from JWT token
     *
     * @param token JWT token
     * @return Claims object
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if token is expired
     *
     * @param token JWT token
     * @return true if expired, false otherwise
     */
    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validate JWT token
     *
     * @param token JWT token
     * @param username username to validate against
     * @return true if valid, false otherwise
     */
    public Boolean validateToken(String token, String username) {
        try {
            final String extractedUsername = extractUsername(token);
            return (extractedUsername.equals(username) && !isTokenExpired(token));
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return false;
        }
    }
}
