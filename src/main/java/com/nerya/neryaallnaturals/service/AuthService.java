package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Authenticate user with username/email and password, and return JWT token
     *
     * @param usernameOrEmail username or email for authentication
     * @param password raw password
     * @return JWT token if authentication successful, empty Optional otherwise
     */
    public Optional<String> authenticate(String usernameOrEmail, String password) {
        log.info("Authentication attempt for: {}", usernameOrEmail);

        // Fetch user by username or email
        Optional<User> userOptional = userService.getUserForAuthentication(usernameOrEmail);

        if (userOptional.isEmpty()) {
            log.warn("User not found: {}", usernameOrEmail);
            return Optional.empty();
        }

        User user = userOptional.get();

        // Check if user is active
        if (!user.getIsActive()) {
            log.warn("User account is inactive: {}", usernameOrEmail);
            return Optional.empty();
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Invalid password for user: {}", usernameOrEmail);
            return Optional.empty();
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(user);
        log.info("Authentication successful for user: {}", user.getUsername());

        return Optional.of(token);
    }

    /**
     * Validate JWT token
     *
     * @param token JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            String username = jwtUtil.extractUsername(token);
            return jwtUtil.validateToken(token, username);
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract username from JWT token
     *
     * @param token JWT token
     * @return username
     */
    public String getUsernameFromToken(String token) {
        return jwtUtil.extractUsername(token);
    }

    /**
     * Extract email from JWT token
     *
     * @param token JWT token
     * @return email
     */
    public String getEmailFromToken(String token) {
        return jwtUtil.extractEmail(token);
    }
}
