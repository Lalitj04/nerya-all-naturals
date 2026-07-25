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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** T56/T57: one review per customer per product, moderation, verified-purchase rule. */
class ReviewIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createReview_isPubliclyVisible_andRecomputesProductRating() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Review Cat");
        long product = createProduct(admin, "Reviewable", "REV-1", 100, categoryId, 10);
        String customer = register("rachel", "rachel@example.com").getToken();

        JsonNode review = createReview(customer, product, 5, "Great!", HttpStatus.CREATED);
        assertThat(review.get("rating").asInt()).isEqualTo(5);
        assertThat(review.get("verifiedPurchase").asBoolean()).isFalse();

        // Public, unauthenticated read.
        ResponseEntity<JsonNode> publicRead = rest.exchange(
                "/api/products/" + product + "/reviews", HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class);
        assertThat(publicRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicRead.getBody().get("totalElements").asInt()).isEqualTo(1);

        JsonNode productJson = getJson(admin, "/api/products/" + product);
        assertThat(productJson.get("averageRating").asDouble()).isEqualTo(5.0);
        assertThat(productJson.get("totalReviews").asInt()).isEqualTo(1);
    }

    @Test
    void secondReview_forSameProduct_isRejected() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Review Cat 2");
        long product = createProduct(admin, "OnceOnly", "REV-2", 100, categoryId, 10);
        String customer = register("ronny", "ronny@example.com").getToken();

        createReview(customer, product, 4, "Good", HttpStatus.CREATED);
        createReview(customer, product, 2, "Changed my mind", HttpStatus.CONFLICT);
    }

    @Test
    void updateAndDeleteOwnReview() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Review Cat 3");
        long product = createProduct(admin, "Editable", "REV-3", 100, categoryId, 10);
        String customer = register("rosa", "rosa@example.com").getToken();

        createReview(customer, product, 3, "Meh", HttpStatus.CREATED);

        ResponseEntity<JsonNode> updated = rest.exchange("/api/products/" + product + "/reviews/me",
                HttpMethod.PUT, bearer(customer, Map.of("rating", 5, "title", "Actually great")), JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("rating").asInt()).isEqualTo(5);

        ResponseEntity<Void> deleted = rest.exchange("/api/products/" + product + "/reviews/me",
                HttpMethod.DELETE, bearer(customer), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode productJson = getJson(admin, "/api/products/" + product);
        assertThat(productJson.get("totalReviews").asInt()).isZero();
    }

    @Test
    void adminCanModerateAwayAnyReview() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Review Cat 4");
        long product = createProduct(admin, "Moderated", "REV-4", 100, categoryId, 10);
        String customer = register("moe", "moe@example.com").getToken();

        JsonNode review = createReview(customer, product, 1, "Spam", HttpStatus.CREATED);
        long reviewId = review.get("id").asLong();

        ResponseEntity<Void> removed = rest.exchange("/api/reviews/admin/" + reviewId,
                HttpMethod.DELETE, bearer(admin), Void.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> publicRead = rest.exchange(
                "/api/products/" + product + "/reviews", HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class);
        assertThat(publicRead.getBody().get("totalElements").asInt()).isZero();
    }

    @Test
    void reviewingProductAfterDeliveredOrder_isMarkedVerifiedPurchase() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Review Cat 5");
        long product = createProduct(admin, "Delivered", "REV-5", 100, categoryId, 10);

        String customer = register("vera", "vera@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 1);
        String orderNumber = checkout(customer, addressId).get("orderNumber").asText();
        setStatus(admin, orderNumber, "CONFIRMED");
        setStatus(admin, orderNumber, "PACKED");
        setStatus(admin, orderNumber, "SHIPPED");
        setStatus(admin, orderNumber, "DELIVERED");

        JsonNode review = createReview(customer, product, 5, "Loved it", HttpStatus.CREATED);
        assertThat(review.get("verifiedPurchase").asBoolean()).isTrue();
    }

    // ---- helpers ----

    private JsonNode createReview(String token, long productId, int rating, String title, HttpStatus expected) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/products/" + productId + "/reviews/me",
                HttpMethod.POST, bearer(token, Map.of("rating", rating, "title", title)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
        return response.getBody();
    }

    private void setStatus(String token, String orderNumber, String status) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/orders/admin/" + orderNumber + "/status",
                HttpMethod.PUT, bearer(token, Map.of("status", status)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private long createCategory(String token, String name) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/categories/admin", HttpMethod.POST,
                bearer(token, Map.of("name", name, "isActive", true)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    private long createProduct(String token, String name, String sku, int sellingPrice, long categoryId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("sku", sku);
        body.put("price", sellingPrice + 10);
        body.put("sellingPrice", sellingPrice);
        body.put("categoryId", categoryId);
        body.put("quantity", quantity);
        ResponseEntity<JsonNode> response = rest.exchange("/api/products/admin", HttpMethod.POST,
                bearer(token, body), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    private long createAddress(String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Home");
        body.put("addressLine1", "1 Test St");
        body.put("city", "Mumbai");
        body.put("state", "MH");
        body.put("pinCode", "400001");
        body.put("isDefault", true);
        ResponseEntity<JsonNode> response = rest.exchange("/api/customers/me/addresses", HttpMethod.POST,
                bearer(token, body), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    private void addToCart(String token, long productId, int quantity) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/cart/items", HttpMethod.POST,
                bearer(token, Map.of("productId", productId, "quantity", quantity)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode checkout(String token, long addressId) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/orders/checkout", HttpMethod.POST,
                bearer(token, Map.of("addressId", addressId)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private JsonNode getJson(String token, String path) {
        ResponseEntity<JsonNode> response = rest.exchange(path, HttpMethod.GET, bearer(token), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
