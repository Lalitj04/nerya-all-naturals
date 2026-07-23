package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.dto.AuthResponse;
import com.nerya.neryaallnaturals.dto.LoginRequest;
import com.nerya.neryaallnaturals.dto.RefreshTokenRequest;
import com.nerya.neryaallnaturals.dto.RegisterRequest;
import com.nerya.neryaallnaturals.entity.Customer;
import com.nerya.neryaallnaturals.entity.RefreshToken;
import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.service.AuthService;
import com.nerya.neryaallnaturals.service.CustomerService;
import com.nerya.neryaallnaturals.service.RefreshTokenService;
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
    private final CustomerService customerService;
    private final RefreshTokenService refreshTokenService;

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
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .type("Bearer")
                    .refreshToken(refreshToken.getToken())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .roles(user.getRoles())
                    .customerId(customerService.findCustomerIdByUsername(user.getUsername()).orElse(null))
                    .build();

            log.info("Login successful for: {}", user.getUsername());
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error generating authentication response");
    }

    /**
     * Registration endpoint - creates a User (ROLE_CUSTOMER) and its linked Customer
     * profile in one transaction, then logs the new customer straight in.
     *
     * @param registerRequest the new customer's details
     * @return JWT token with user details, same shape as login
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("Registration attempt for: {}", registerRequest.getUsername());

        Customer customer = customerService.registerCustomer(registerRequest);
        User user = customer.getUser();
        String token = authService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        AuthResponse response = AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .customerId(customer.getId())
                .build();

        log.info("Registration successful for: {}", user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Exchange a still-valid refresh token for a new access token. The refresh token
     * itself is not rotated — the same one keeps working until it expires or is revoked.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.requireValid(request.getRefreshToken());
        User user = refreshToken.getUser();
        String newAccessToken = authService.generateToken(user);

        AuthResponse response = AuthResponse.builder()
                .token(newAccessToken)
                .type("Bearer")
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .customerId(customerService.findCustomerIdByUsername(user.getUsername()).orElse(null))
                .build();

        log.info("Access token refreshed for: {}", user.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Revoke a refresh token, ending that session. The still-live access token remains
     * valid until it naturally expires (stateless JWTs aren't revocable), but no new
     * access token can be minted from this refresh token afterward.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Validate token endpoint. The token is read from the {@code Authorization: Bearer}
     * header rather than a query parameter, so it is not exposed in access logs, proxies,
     * or browser history.
     *
     * @param authorizationHeader the {@code Authorization} request header
     * @return validation result
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing or malformed Authorization header");
        }

        if (authService.validateToken(token)) {
            String username = authService.getUsernameFromToken(token);
            return ResponseEntity.ok("Token is valid for user: " + username);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
    }

    /**
     * Extract the bearer token from an {@code Authorization} header value.
     *
     * @param authorizationHeader the raw header value (may be null)
     * @return the token, or null if the header is missing or not a Bearer token
     */
    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }
}
