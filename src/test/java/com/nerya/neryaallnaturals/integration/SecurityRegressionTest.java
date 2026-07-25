package com.nerya.neryaallnaturals.integration;

import com.nerya.neryaallnaturals.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** T58/T59 security regressions: forged JWTs and brute-force login throttling. */
class SecurityRegressionTest extends AbstractIntegrationTest {

    @Test
    void forgedJwt_isRejected() {
        // No AuthenticationEntryPoint is configured (no httpBasic/formLogin), so an
        // unauthenticated/invalid-token request falls through to the same 403 Spring Security
        // uses for role denials (see IdentityIntegrationTest#adminGatedEndpoint_rejectsCustomer).
        ResponseEntity<String> response = rest.exchange("/api/orders/me", HttpMethod.GET,
                bearerRaw("this.is.not-a-real-jwt"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void repeatedFailedLogins_areRateLimited() {
        // A username unique to this test, and a synthetic source IP, so this test's failures
        // can't be pushed over the limit by unrelated failed-login attempts elsewhere in the
        // suite (e.g. IdentityIntegrationTest#login_wrongPassword_returnsUnauthorized) sharing
        // the loopback address, nor vice versa.
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = failedLoginAttempt();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(failedLoginAttempt().getStatusCode().value()).isEqualTo(429);
    }

    private ResponseEntity<String> failedLoginAttempt() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "203.0.113.55");
        HttpEntity<LoginRequest> entity =
                new HttpEntity<>(new LoginRequest("nosuchuser-ratelimit", "wrong"), headers);
        return rest.postForEntity("/api/auth/login", entity, String.class);
    }

    private HttpEntity<Void> bearerRaw(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return new HttpEntity<>(headers);
    }
}
