package com.nerya.neryaallnaturals.config;

import com.nerya.neryaallnaturals.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Explicit auth endpoints only — avoid a blanket /api/auth/** that would
                // silently expose any future endpoint added under that prefix.
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/validate").permitAll()
                .requestMatchers("/api/health", "/api/ping").permitAll() // Deployment check endpoints
                // Public catalog reads — /admin/** sub-paths on these controllers stay
                // authenticated below since they're not matched by these single/segment
                // patterns (checked against the controllers' actual admin routes).
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/{id}", "/api/products/category/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/{id}", "/api/categories/parents").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/media", "/api/media/{id}", "/api/media/category/**", "/api/media/product/**").permitAll()

                // All other requests require authentication
                // Authorization is handled by method-level annotations (@AdminOnly, @CustomerOnly, etc.)
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}


