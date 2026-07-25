package com.nerya.neryaallnaturals.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.nerya.neryaallnaturals.dto.AuthResponse;
import com.nerya.neryaallnaturals.dto.LoginRequest;
import com.nerya.neryaallnaturals.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** T64/T66/T67: admin draft/publish lifecycle, public visibility rules, slug handling. */
class BlogIntegrationTest extends AbstractIntegrationTest {

    @Test
    void draftIsNotPubliclyVisible_untilPublished() {
        String admin = adminToken();
        JsonNode draft = createDraft(admin, "My First Post", "Some content");
        String slug = draft.get("slug").asText();
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");

        // Not visible in the public list, and 404 by slug even though it's the right slug.
        assertThat(getPublicList().get("totalElements").asInt()).isZero();
        ResponseEntity<JsonNode> bySlug = rest.getForEntity("/api/blogs/" + slug, JsonNode.class);
        assertThat(bySlug.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // But visible to admin (id-based check — the admin listing is global across the whole
        // test class, so other tests' drafts may also be present).
        assertThat(getJson(admin, "/api/blogs/admin/" + draft.get("id").asLong()).get("status").asText())
                .isEqualTo("DRAFT");
    }

    @Test
    void publishing_makesItPubliclyVisible_unpublishHidesItAgain() {
        String admin = adminToken();
        long id = createDraft(admin, "Publish Me", "Body text").get("id").asLong();

        JsonNode published = post(admin, "/api/blogs/admin/" + id + "/publish");
        assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");
        String slug = published.get("slug").asText();
        assertThat(published.get("publishedAt").isNull()).isFalse();

        JsonNode publicRead = rest.getForEntity("/api/blogs/" + slug, JsonNode.class).getBody();
        assertThat(publicRead.get("title").asText()).isEqualTo("Publish Me");
        assertThat(getPublicList().get("totalElements").asInt()).isEqualTo(1);

        JsonNode unpublished = post(admin, "/api/blogs/admin/" + id + "/unpublish");
        assertThat(unpublished.get("status").asText()).isEqualTo("DRAFT");
        assertThat(getPublicList().get("totalElements").asInt()).isZero();
        ResponseEntity<JsonNode> afterUnpublish = rest.getForEntity("/api/blogs/" + slug, JsonNode.class);
        assertThat(afterUnpublish.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Still visible to admin, not deleted.
        assertThat(getJson(admin, "/api/blogs/admin/" + id).get("id").asLong()).isEqualTo(id);
    }

    @Test
    void publishingWithoutContent_isRejected() {
        String admin = adminToken();
        long id = createDraft(admin, "Empty Post", null).get("id").asLong();

        ResponseEntity<JsonNode> response = rest.exchange("/api/blogs/admin/" + id + "/publish",
                HttpMethod.POST, bearer(admin), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void duplicateTitles_getCollisionSuffixedSlugs() {
        String admin = adminToken();
        JsonNode first = createDraft(admin, "Same Title", "First body");
        JsonNode second = createDraft(admin, "Same Title", "Second body");

        assertThat(first.get("slug").asText()).isEqualTo("same-title");
        assertThat(second.get("slug").asText()).isEqualTo("same-title-2");
    }

    @Test
    void nonAdmin_cannotWriteBlogs() {
        String customer = register("blogreader", "blogreader@example.com").getToken();

        ResponseEntity<JsonNode> response = rest.exchange("/api/blogs/admin", HttpMethod.POST,
                bearer(customer, Map.of("title", "Hack", "content", "nope")), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- helpers ----

    private JsonNode createDraft(String token, String title, String content) {
        Map<String, Object> body = content == null
                ? Map.of("title", title)
                : Map.of("title", title, "content", content);
        ResponseEntity<JsonNode> response = rest.exchange("/api/blogs/admin", HttpMethod.POST,
                bearer(token, body), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private JsonNode getPublicList() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/blogs", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode post(String token, String path) {
        ResponseEntity<JsonNode> response = rest.exchange(path, HttpMethod.POST, bearer(token), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode getJson(String token, String path) {
        ResponseEntity<JsonNode> response = rest.exchange(path, HttpMethod.GET, bearer(token), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String adminToken() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/login",
                new LoginRequest("testadmin", "AdminPass123!"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("token").asText();
    }

    private AuthResponse register(String username, String email) {
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/auth/register",
                new RegisterRequest(username, email, "password123", "First", "Last", null), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
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
