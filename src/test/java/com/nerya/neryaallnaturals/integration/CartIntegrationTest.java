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

class CartIntegrationTest extends AbstractIntegrationTest {

    // ---- T42 / T43: add, update, remove, clear + stock enforcement ----

    @Test
    void cart_addUpdateRemoveClear_recomputesTotals() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Cart Cat");
        long widget = createProduct(admin, "Widget", "CART-W", 100, categoryId, 50);
        long gadget = createProduct(admin, "Gadget", "CART-G", 250, categoryId, 50);

        String customer = register("carla", "carla@example.com").getToken();

        // Add two lines.
        JsonNode afterAdd = post(customer, "/api/cart/items", Map.of("productId", widget, "quantity", 2));
        assertThat(afterAdd.get("items")).hasSize(1);
        assertThat(afterAdd.get("grandTotal").asInt()).isEqualTo(200);

        JsonNode twoLines = post(customer, "/api/cart/items", Map.of("productId", gadget, "quantity", 1));
        assertThat(twoLines.get("items")).hasSize(2);
        assertThat(twoLines.get("totalItems").asInt()).isEqualTo(3);
        assertThat(twoLines.get("grandTotal").asInt()).isEqualTo(450);

        // Adding the same product merges into the existing line.
        JsonNode merged = post(customer, "/api/cart/items", Map.of("productId", widget, "quantity", 3));
        assertThat(merged.get("items")).hasSize(2);
        assertThat(lineQty(merged, widget)).isEqualTo(5);
        assertThat(merged.get("grandTotal").asInt()).isEqualTo(750); // 5*100 + 1*250

        // Set an exact quantity.
        JsonNode updated = put(customer, "/api/cart/items/" + widget, Map.of("quantity", 1));
        assertThat(lineQty(updated, widget)).isEqualTo(1);
        assertThat(updated.get("grandTotal").asInt()).isEqualTo(350);

        // Quantity 0 removes the line.
        JsonNode removedViaZero = put(customer, "/api/cart/items/" + widget, Map.of("quantity", 0));
        assertThat(removedViaZero.get("items")).hasSize(1);

        // Explicit delete removes the remaining line.
        ResponseEntity<JsonNode> removed = rest.exchange("/api/cart/items/" + gadget, HttpMethod.DELETE,
                bearer(customer), JsonNode.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(removed.getBody().get("items")).isEmpty();
        assertThat(removed.getBody().get("grandTotal").asInt()).isZero();

        // Clear on an already-empty cart is a no-op that still returns 200.
        ResponseEntity<JsonNode> cleared = rest.exchange("/api/cart", HttpMethod.DELETE,
                bearer(customer), JsonNode.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("items")).isEmpty();
    }

    @Test
    void cart_clear_emptiesAllLines() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Clear Cat");
        long product = createProduct(admin, "Clearable", "CART-CLR", 100, categoryId, 50);

        String customer = register("clive", "clive@example.com").getToken();
        post(customer, "/api/cart/items", Map.of("productId", product, "quantity", 3));

        ResponseEntity<JsonNode> cleared = rest.exchange("/api/cart", HttpMethod.DELETE,
                bearer(customer), JsonNode.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("items")).isEmpty();

        assertThat(getJson(customer, "/api/cart").get("items")).isEmpty();
    }

    @Test
    void cart_add_beyondAvailableStock_isRejected() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Scarce Cat");
        long product = createProduct(admin, "Scarce", "CART-SC", 100, categoryId, 3);

        String customer = register("stan", "stan@example.com").getToken();

        // Requesting more than the 3 on hand is rejected.
        ResponseEntity<JsonNode> tooMany = rest.exchange("/api/cart/items", HttpMethod.POST,
                bearer(customer, Map.of("productId", product, "quantity", 4)), JsonNode.class);
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooMany.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");

        // Adding exactly the stock succeeds...
        JsonNode ok = post(customer, "/api/cart/items", Map.of("productId", product, "quantity", 3));
        assertThat(lineQty(ok, product)).isEqualTo(3);

        // ...but a merge that would exceed stock is rejected, leaving the line untouched.
        ResponseEntity<JsonNode> merge = rest.exchange("/api/cart/items", HttpMethod.POST,
                bearer(customer, Map.of("productId", product, "quantity", 1)), JsonNode.class);
        assertThat(merge.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(merge.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(lineQty(getJson(customer, "/api/cart"), product)).isEqualTo(3);
    }

    @Test
    void cart_update_beyondAvailableStock_isRejected() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Update Cat");
        long product = createProduct(admin, "Limited", "CART-LIM", 100, categoryId, 2);

        String customer = register("uma", "uma@example.com").getToken();
        post(customer, "/api/cart/items", Map.of("productId", product, "quantity", 1));

        ResponseEntity<JsonNode> update = rest.exchange("/api/cart/items/" + product, HttpMethod.PUT,
                bearer(customer, Map.of("quantity", 5)), JsonNode.class);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(update.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
    }

    // ---- cross-customer isolation ----

    @Test
    void cart_isScopedToTheOwningCustomer() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Iso Cat");
        long product = createProduct(admin, "Shared Product", "CART-ISO", 100, categoryId, 50);

        String alice = register("aisha", "aisha@example.com").getToken();
        String bob = register("bilal", "bilal@example.com").getToken();

        post(alice, "/api/cart/items", Map.of("productId", product, "quantity", 4));

        // Bob's cart is his own — Alice's items are invisible to him.
        assertThat(getJson(bob, "/api/cart").get("items")).isEmpty();

        // Alice still sees her line.
        assertThat(getJson(alice, "/api/cart").get("items")).hasSize(1);
    }

    @Test
    void cart_rejectsUnauthenticated() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/cart", JsonNode.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ---- helpers ----

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

    private JsonNode post(String token, String path, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = rest.exchange(path, HttpMethod.POST, bearer(token, body), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode put(String token, String path, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = rest.exchange(path, HttpMethod.PUT, bearer(token, body), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode getJson(String token, String path) {
        ResponseEntity<JsonNode> response = rest.exchange(path, HttpMethod.GET, bearer(token), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private int lineQty(JsonNode cart, long productId) {
        for (JsonNode item : cart.get("items")) {
            if (item.get("productId").asLong() == productId) {
                return item.get("quantity").asInt();
            }
        }
        throw new AssertionError("Product " + productId + " not found in cart");
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
