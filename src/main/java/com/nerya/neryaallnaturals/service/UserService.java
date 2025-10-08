package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

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
}
