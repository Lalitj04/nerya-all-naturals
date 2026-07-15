package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.UserRequest;
import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Fetch user details by username for authentication purpose
     *
     * @param username the username to search for
     * @return Optional containing the user if found
     */
    public Optional<User> getUserByUsername(String username) {
        log.debug("Fetching user details for username: {}", username);
        return userRepository.findByUsername(username);
    }

    /**
     * Fetch user details by email for authentication purpose
     *
     * @param email the email to search for
     * @return Optional containing the user if found
     */
    public Optional<User> getUserByEmail(String email) {
        log.debug("Fetching user details for email: {}", email);
        return userRepository.findByEmail(email);
    }

    /**
     * Fetch user details by username or email for authentication purpose
     * This method tries username first, then email if username is not found
     *
     * @param usernameOrEmail the username or email to search for
     * @return Optional containing the user if found
     */
    public Optional<User> getUserForAuthentication(String usernameOrEmail) {
        log.debug("Fetching user details for authentication: {}", usernameOrEmail);

        // Try to find by username first
        Optional<User> user = userRepository.findByUsername(usernameOrEmail);

        // If not found by username, try by email
        if (user.isEmpty()) {
            user = userRepository.findByEmail(usernameOrEmail);
        }

        return user;
    }

    /**
     * Fetch a user by id.
     *
     * @param id the user id
     * @return Optional containing the user if found
     */
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * @return all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * @return true if a user with this username already exists
     */
    public boolean usernameExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    /**
     * @return true if a user with this email already exists
     */
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    /**
     * Create a new user from the given request. The password is BCrypt-encoded here.
     * Callers are responsible for uniqueness checks (see {@link #usernameExists}/{@link #emailExists}).
     *
     * @param request the user details
     * @return the persisted user
     */
    @Transactional
    public User createUser(UserRequest request) {
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .isActive(true)
                .isEmailVerified(false)
                .roles(request.getRoles() != null ? request.getRoles() : new HashSet<>())
                .build();

        User saved = userRepository.save(user);
        log.info("User created successfully: {}", saved.getUsername());
        return saved;
    }

    /**
     * Apply the request's fields to an existing user and persist it. The password is
     * re-encoded only when a non-empty value is supplied. Roles are applied only when
     * {@code canChangeRoles} is true, so a non-admin self-update can never escalate
     * privileges. Callers are responsible for uniqueness checks on any changed
     * username/email.
     *
     * @param user           the existing user to modify
     * @param request        the new field values
     * @param canChangeRoles whether the caller is permitted to change roles (admin only)
     * @return the persisted user
     */
    @Transactional
    public User updateUser(User user, UserRequest request, boolean canChangeRoles) {
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRoles() != null) {
            if (canChangeRoles) {
                user.setRoles(request.getRoles());
            } else {
                log.warn("Role change requested for user '{}' by a non-admin caller; ignoring roles field",
                        user.getUsername());
            }
        }

        User updated = userRepository.save(user);
        log.info("User updated successfully: {}", updated.getUsername());
        return updated;
    }

    /**
     * Delete a user.
     *
     * @param user the user to delete
     */
    @Transactional
    public void deleteUser(User user) {
        userRepository.delete(user);
        log.info("User deleted successfully with ID: {}", user.getId());
    }
}
