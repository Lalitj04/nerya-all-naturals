package com.nerya.neryaallnaturals.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.nerya.neryaallnaturals.dto.AddressRequest;
import com.nerya.neryaallnaturals.dto.AuthResponse;
import com.nerya.neryaallnaturals.dto.LoginRequest;
import com.nerya.neryaallnaturals.dto.RegisterRequest;
import com.nerya.neryaallnaturals.dto.UserRequest;
import com.nerya.neryaallnaturals.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityIntegrationTest extends AbstractIntegrationTest {

    // ---- T27 / T28: registration + login ----

    @Test
    void register_thenLogin_issuesCustomerTokenWithRolesAndCustomerId() {
        AuthResponse registered = register("alice", "alice@example.com", "password123", "Alice", "Adams");

        assertThat(registered.getToken()).isNotBlank();
        assertThat(registered.getRefreshToken()).isNotBlank();
        assertThat(registered.getRoles()).containsExactly(User.Role.ROLE_CUSTOMER);
        assertThat(registered.getCustomerId()).isNotNull();

        ResponseEntity<AuthResponse> login = rest.postForEntity("/api/auth/login",
                new LoginRequest("alice", "password123"), AuthResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().getRoles()).containsExactly(User.Role.ROLE_CUSTOMER);
        assertThat(login.getBody().getCustomerId()).isEqualTo(registered.getCustomerId());
    }

    @Test
    void register_duplicateUsername_returnsConflict() {
        register("bob", "bob@example.com", "password123", "Bob", "Brown");

        ResponseEntity<JsonNode> second = rest.postForEntity("/api/auth/register",
                new RegisterRequest("bob", "bob2@example.com", "password123", "Bob", "Brown", null),
                JsonNode.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code").asText()).isEqualTo("CONFLICT");
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() {
        register("carol", "carol@example.com", "password123", "Carol", "Clark");

        ResponseEntity<String> login = rest.postForEntity("/api/auth/login",
                new LoginRequest("carol", "wrong-password"), String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- T30: customer profile resolved from principal, never a path id ----

    @Test
    void customerProfile_meEndpoint_returnsOwnProfile() {
        AuthResponse auth = register("dave", "dave@example.com", "password123", "Dave", "Davis");

        ResponseEntity<JsonNode> me = rest.exchange("/api/customers/me", HttpMethod.GET,
                bearer(auth.getToken()), JsonNode.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("username").asText()).isEqualTo("dave");
        assertThat(me.getBody().get("email").asText()).isEqualTo("dave@example.com");
    }

    // ---- T31: address CRUD + ownership isolation ----

    @Test
    void addressCrud_isScopedToTheOwningCustomer() {
        AuthResponse alice = register("erin", "erin@example.com", "password123", "Erin", "Ellis");
        AuthResponse bob = register("frank", "frank@example.com", "password123", "Frank", "Ford");

        // Erin creates an address.
        AddressRequest request = AddressRequest.builder()
                .name("Home").addressLine1("1 Erin St").city("Mumbai").state("MH").pinCode("400001")
                .isDefault(true).build();
        ResponseEntity<JsonNode> created = rest.exchange("/api/customers/me/addresses", HttpMethod.POST,
                bearer(alice.getToken(), request), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long addressId = created.getBody().get("id").asLong();
        assertThat(created.getBody().get("isDefault").asBoolean()).isTrue();

        // Frank cannot see Erin's address in his own list.
        ResponseEntity<JsonNode> franksList = rest.exchange("/api/customers/me/addresses", HttpMethod.GET,
                bearer(bob.getToken()), JsonNode.class);
        assertThat(franksList.getBody()).isEmpty();

        // Frank cannot update Erin's address by id — indistinguishable from not found.
        ResponseEntity<JsonNode> frankUpdate = rest.exchange("/api/customers/me/addresses/" + addressId,
                HttpMethod.PUT, bearer(bob.getToken(), request), JsonNode.class);
        assertThat(frankUpdate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Frank cannot delete it either.
        ResponseEntity<Void> frankDelete = rest.exchange("/api/customers/me/addresses/" + addressId,
                HttpMethod.DELETE, bearer(bob.getToken()), Void.class);
        assertThat(frankDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Erin's address is untouched.
        ResponseEntity<JsonNode> erinsList = rest.exchange("/api/customers/me/addresses", HttpMethod.GET,
                bearer(alice.getToken()), JsonNode.class);
        assertThat(erinsList.getBody()).hasSize(1);
    }

    @Test
    void addressDefaultFlag_isUniquePerCustomer() {
        AuthResponse auth = register("grace", "grace@example.com", "password123", "Grace", "Green");

        createAddress(auth.getToken(), "Home", "400001", true);
        createAddress(auth.getToken(), "Work", "411001", true);

        ResponseEntity<JsonNode> list = rest.exchange("/api/customers/me/addresses", HttpMethod.GET,
                bearer(auth.getToken()), JsonNode.class);

        long defaults = 0;
        for (JsonNode addr : list.getBody()) {
            if (addr.get("isDefault").asBoolean()) {
                defaults++;
            }
        }
        assertThat(defaults).isEqualTo(1);
        // The most recently defaulted address (Work) wins.
        assertThat(list.getBody().get(0).get("name").asText()).isEqualTo("Work");
    }

    // ---- T32: refresh + logout ----

    @Test
    void refreshToken_issuesNewAccessToken_andLogoutRevokesIt() {
        AuthResponse auth = register("heidi", "heidi@example.com", "password123", "Heidi", "Hall");

        Map<String, String> refreshBody = Map.of("refreshToken", auth.getRefreshToken());

        ResponseEntity<AuthResponse> refreshed = rest.postForEntity("/api/auth/refresh", refreshBody, AuthResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().getToken()).isNotBlank();

        ResponseEntity<Void> logout = rest.postForEntity("/api/auth/logout", refreshBody, Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> afterLogout = rest.postForEntity("/api/auth/refresh", refreshBody, JsonNode.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshToken_invalidToken_returnsUnauthorized() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/refresh",
                Map.of("refreshToken", "not-a-real-token"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- T3 regression: a non-admin can never escalate their own roles ----

    @Test
    void selfUpdate_cannotEscalateRoles() {
        String adminToken = login("testadmin", "AdminPass123!").getToken();

        // Admin creates a plain ROLE_USER account.
        UserRequest newUser = UserRequest.builder()
                .username("mallory").email("mallory@example.com").password("password123")
                .firstName("Mallory").lastName("Moore").roles(Set.of(User.Role.ROLE_USER))
                .build();
        ResponseEntity<JsonNode> created = rest.exchange("/api/users", HttpMethod.POST,
                bearer(adminToken, newUser), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long userId = created.getBody().get("id").asLong();

        String userToken = login("mallory", "password123").getToken();

        // Mallory tries to make herself an admin via a self-update. Password is a valid
        // value (an empty one fails bean validation before the role logic is even reached).
        UserRequest escalation = UserRequest.builder()
                .username("mallory").email("mallory@example.com").password("password123")
                .firstName("Mallory").lastName("Moore").roles(Set.of(User.Role.ROLE_ADMIN))
                .build();
        ResponseEntity<JsonNode> selfUpdate = rest.exchange("/api/users/" + userId, HttpMethod.PUT,
                bearer(userToken, escalation), JsonNode.class);

        assertThat(selfUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode roles = selfUpdate.getBody().get("roles");
        assertThat(roles.toString()).contains("ROLE_USER").doesNotContain("ROLE_ADMIN");
    }

    @Test
    void adminGatedEndpoint_rejectsCustomer() {
        AuthResponse customer = register("nina", "nina@example.com", "password123", "Nina", "Nolan");

        ResponseEntity<JsonNode> response = rest.exchange("/api/users", HttpMethod.GET,
                bearer(customer.getToken()), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- helpers ----

    private AuthResponse register(String username, String email, String password, String first, String last) {
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/auth/register",
                new RegisterRequest(username, email, password, first, last, null), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private AuthResponse login(String usernameOrEmail, String password) {
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/auth/login",
                new LoginRequest(usernameOrEmail, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void createAddress(String token, String name, String pinCode, boolean isDefault) {
        AddressRequest request = AddressRequest.builder()
                .name(name).addressLine1("1 " + name + " St").city("Mumbai").state("MH")
                .pinCode(pinCode).isDefault(isDefault).build();
        ResponseEntity<JsonNode> response = rest.exchange("/api/customers/me/addresses", HttpMethod.POST,
                bearer(token, request), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> bearer(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
