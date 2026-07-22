package com.nerya.neryaallnaturals.config;

import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates the first admin user from ADMIN_USERNAME/ADMIN_PASSWORD when the database has
 * no admin yet. No-op once an admin exists, so it's safe to leave enabled across restarts
 * and deploys.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username:}")
    private String adminUsername;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${admin.email:}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        if (userRepository.existsByRolesContaining(User.Role.ROLE_ADMIN)) {
            log.debug("Admin bootstrap: an admin user already exists; skipping");
            return;
        }

        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            log.warn("Admin bootstrap: no admin exists and ADMIN_USERNAME/ADMIN_PASSWORD are not set; skipping");
            return;
        }

        if (userRepository.findByUsername(adminUsername).isPresent()) {
            log.warn("Admin bootstrap: a user named '{}' already exists without the admin role; leaving it unchanged",
                    adminUsername);
            return;
        }

        String email = adminEmail.isBlank() ? adminUsername + "@admin.local" : adminEmail;

        User admin = User.builder()
                .username(adminUsername)
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .firstName("Admin")
                .lastName("User")
                .isActive(true)
                .isEmailVerified(true)
                .roles(Set.of(User.Role.ROLE_ADMIN))
                .build();

        userRepository.save(admin);
        log.info("Admin bootstrap: created initial admin user '{}'", adminUsername);
    }
}
