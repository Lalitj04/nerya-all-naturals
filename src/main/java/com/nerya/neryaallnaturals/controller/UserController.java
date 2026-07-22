package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.annotation.AdminOrUser;
import com.nerya.neryaallnaturals.dto.UserRequest;
import com.nerya.neryaallnaturals.dto.UserResponse;
import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ForbiddenException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Create a new user
     * Only ADMIN can create users
     *
     * @param userRequest user details
     * @return created user
     */
    @PostMapping
    @AdminOnly
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest userRequest) {
        log.info("Creating new user: {}", userRequest.getUsername());

        if (userService.usernameExists(userRequest.getUsername())) {
            throw new ConflictException("Username already exists");
        }

        if (userService.emailExists(userRequest.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        User savedUser = userService.createUser(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromEntity(savedUser));
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
        List<UserResponse> users = userService.getAllUsers().stream()
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
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id,
                                         Authentication authentication) {
        log.info("Fetching user with ID: {}", id);
        User user = userService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!isAdmin(authentication) && !isOwner(authentication, user)) {
            log.warn("Non-admin caller '{}' attempted to access user ID {}", authentication.getName(), id);
            throw new ForbiddenException("Access denied");
        }

        return ResponseEntity.ok(UserResponse.fromEntity(user));
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
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                        @Valid @RequestBody UserRequest userRequest,
                                        Authentication authentication) {
        log.info("Updating user with ID: {}", id);
        User user = userService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!isAdmin(authentication) && !isOwner(authentication, user)) {
            log.warn("Non-admin caller '{}' attempted to update user ID {}", authentication.getName(), id);
            throw new ForbiddenException("Access denied");
        }

        // Reject username/email changes that collide with another account
        if (!user.getUsername().equals(userRequest.getUsername())
                && userService.usernameExists(userRequest.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        if (!user.getEmail().equals(userRequest.getEmail())
                && userService.emailExists(userRequest.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        User updatedUser = userService.updateUser(user, userRequest, isAdmin(authentication));
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
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("Deleting user with ID: {}", id);
        User user = userService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        userService.deleteUser(user);
        return ResponseEntity.noContent().build();
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
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username,
                                               Authentication authentication) {
        log.info("Fetching user with username: {}", username);

        if (!isAdmin(authentication) && !username.equals(authentication.getName())) {
            log.warn("Non-admin caller '{}' attempted to access username {}", authentication.getName(), username);
            throw new ForbiddenException("Access denied");
        }

        User user = userService.getUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return ResponseEntity.ok(UserResponse.fromEntity(user));
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
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email,
                                            Authentication authentication) {
        log.info("Fetching user with email: {}", email);
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!isAdmin(authentication) && !isOwner(authentication, user)) {
            log.warn("Non-admin caller '{}' attempted to access email {}", authentication.getName(), email);
            throw new ForbiddenException("Access denied");
        }

        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    /**
     * @return true if the authenticated caller holds ROLE_ADMIN
     */
    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * @return true if the target user is the authenticated caller (matched by username,
     * which is the JWT subject / principal name)
     */
    private boolean isOwner(Authentication authentication, User user) {
        return user.getUsername().equals(authentication.getName());
    }
}
