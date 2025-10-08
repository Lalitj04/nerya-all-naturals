package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.annotation.AdminOrUser;
import com.nerya.neryaallnaturals.dto.UserRequest;
import com.nerya.neryaallnaturals.dto.UserResponse;
import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Create a new user
     * Only ADMIN can create users
     *
     * @param userRequest user details
     * @return created user
     */
    @PostMapping
    @AdminOnly
    public ResponseEntity<?> createUser(@Valid @RequestBody UserRequest userRequest) {
        log.info("Creating new user: {}", userRequest.getUsername());

        // Check if username already exists
        if (userRepository.findByUsername(userRequest.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Username already exists");
        }

        // Check if email already exists
        if (userRepository.findByEmail(userRequest.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Email already exists");
        }

        // Create new user
        User user = User.builder()
                .username(userRequest.getUsername())
                .email(userRequest.getEmail())
                .password(passwordEncoder.encode(userRequest.getPassword()))
                .firstName(userRequest.getFirstName())
                .lastName(userRequest.getLastName())
                .phoneNumber(userRequest.getPhoneNumber())
                .isActive(true)
                .isEmailVerified(false)
                .roles(userRequest.getRoles() != null ? userRequest.getRoles() : new HashSet<>())
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.fromEntity(savedUser));
    }

    /**
     * Get all users
     * Only ADMIN can view all users
     *
     * @return list of all users
     */
    @GetMapping
    @AdminOnly
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        log.info("Fetching all users");
        List<UserResponse> users = userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * Get user by ID
     * ADMIN can view any user, USER can view their own profile
     *
     * @param id user ID
     * @return user details
     */
    @GetMapping("/{id}")
    @AdminOrUser
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        log.info("Fetching user with ID: {}", id);
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found with ID: " + id);
        }

        return ResponseEntity.ok(UserResponse.fromEntity(userOptional.get()));
    }

    /**
     * Update user by ID
     * ADMIN can update any user, USER can update their own profile
     *
     * @param id user ID
     * @param userRequest updated user details
     * @return updated user
     */
    @PutMapping("/{id}")
    @AdminOrUser
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                        @Valid @RequestBody UserRequest userRequest) {
        log.info("Updating user with ID: {}", id);
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found with ID: " + id);
        }

        User user = userOptional.get();

        // Check if username is being changed and if it already exists
        if (!user.getUsername().equals(userRequest.getUsername())) {
            if (userRepository.findByUsername(userRequest.getUsername()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Username already exists");
            }
            user.setUsername(userRequest.getUsername());
        }

        // Check if email is being changed and if it already exists
        if (!user.getEmail().equals(userRequest.getEmail())) {
            if (userRepository.findByEmail(userRequest.getEmail()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Email already exists");
            }
            user.setEmail(userRequest.getEmail());
        }

        // Update fields
        user.setFirstName(userRequest.getFirstName());
        user.setLastName(userRequest.getLastName());
        user.setPhoneNumber(userRequest.getPhoneNumber());

        // Update password if provided
        if (userRequest.getPassword() != null && !userRequest.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userRequest.getPassword()));
        }

        // Update roles if provided
        if (userRequest.getRoles() != null) {
            user.setRoles(userRequest.getRoles());
        }

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully: {}", updatedUser.getUsername());

        return ResponseEntity.ok(UserResponse.fromEntity(updatedUser));
    }

    /**
     * Delete user by ID
     * Only ADMIN can delete users
     *
     * @param id user ID
     * @return success message
     */
    @DeleteMapping("/{id}")
    @AdminOnly
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        log.info("Deleting user with ID: {}", id);
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found with ID: " + id);
        }

        userRepository.deleteById(id);
        log.info("User deleted successfully with ID: {}", id);

        return ResponseEntity.ok("User deleted successfully");
    }

    /**
     * Get user by username
     * ADMIN can view any user, USER can view their own profile
     *
     * @param username username
     * @return user details
     */
    @GetMapping("/username/{username}")
    @AdminOrUser
    public ResponseEntity<?> getUserByUsername(@PathVariable String username) {
        log.info("Fetching user with username: {}", username);
        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found with username: " + username);
        }

        return ResponseEntity.ok(UserResponse.fromEntity(userOptional.get()));
    }

    /**
     * Get user by email
     * ADMIN can view any user, USER can view their own profile
     *
     * @param email email address
     * @return user details
     */
    @GetMapping("/email/{email}")
    @AdminOrUser
    public ResponseEntity<?> getUserByEmail(@PathVariable String email) {
        log.info("Fetching user with email: {}", email);
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found with email: " + email);
        }

        return ResponseEntity.ok(UserResponse.fromEntity(userOptional.get()));
    }
}
