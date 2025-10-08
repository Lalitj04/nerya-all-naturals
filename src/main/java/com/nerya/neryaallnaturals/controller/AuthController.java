package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.dto.AuthResponse;
import com.nerya.neryaallnaturals.dto.LoginRequest;
import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.service.AuthService;
import com.nerya.neryaallnaturals.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * Login endpoint - authenticates user and returns JWT token
     *
     * @param loginRequest contains username/email and password
     * @return JWT token with user details
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for: {}", loginRequest.getUsernameOrEmail());

        Optional<String> tokenOptional = authService.authenticate(
                loginRequest.getUsernameOrEmail(),
                loginRequest.getPassword()
        );

        if (tokenOptional.isEmpty()) {
            log.warn("Login failed for: {}", loginRequest.getUsernameOrEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username/email or password");
        }

        String token = tokenOptional.get();

        // Get user details for response
        Optional<User> userOptional = userService.getUserForAuthentication(loginRequest.getUsernameOrEmail());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .type("Bearer")
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .build();

            log.info("Login successful for: {}", user.getUsername());
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error generating authentication response");
    }

    /**
     * Validate token endpoint
     *
     * @param token JWT token to validate
     * @return validation result
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestParam String token) {
        boolean isValid = authService.validateToken(token);
        if (isValid) {
            String username = authService.getUsernameFromToken(token);
            return ResponseEntity.ok("Token is valid for user: " + username);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
    }
}
