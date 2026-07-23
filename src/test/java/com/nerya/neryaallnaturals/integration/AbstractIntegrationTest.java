package com.nerya.neryaallnaturals.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base for HTTP-level integration tests. Boots the full application on a random port
 * against a real MySQL 8 container, so Flyway migrations run and Hibernate validates the
 * schema exactly as in production.
 *
 * <p>Uses the Testcontainers "singleton container" pattern: the container is started once
 * in a static initializer and shared across every test class that extends this one, with
 * Ryuk stopping it at JVM exit. We deliberately do NOT use {@code @Testcontainers}/
 * {@code @Container} here — those tie the container's lifecycle to a single test class and
 * would stop it after the first integration class finishes, leaving the Spring-cached
 * context of the next class pointing at a dead database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("nerya_test");

    static {
        MYSQL.start();
    }

    @Autowired
    protected TestRestTemplate rest;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // A deterministic >= 32-byte secret so JwtUtil's startup check passes.
        registry.add("jwt.secret", () -> "test-jwt-secret-that-is-definitely-long-enough-for-hs256-1234567890");
        registry.add("jwt.expiration", () -> "3600000");
        // Seed a known admin so tests can exercise admin-gated flows.
        registry.add("admin.username", () -> "testadmin");
        registry.add("admin.password", () -> "AdminPass123!");
        registry.add("admin.email", () -> "admin@test.local");
    }
}
