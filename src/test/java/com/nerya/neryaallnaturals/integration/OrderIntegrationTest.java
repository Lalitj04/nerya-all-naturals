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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIntegrationTest extends AbstractIntegrationTest {

    // ---- T46 / T47: happy-path checkout, history, snapshotting ----

    @Test
    void checkout_placesOrder_snapshotsPrices_andClearsCart() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Order Cat");
        long widget = createProduct(admin, "Widget", "ORD-W", 100, categoryId, 10);
        long gadget = createProduct(admin, "Gadget", "ORD-G", 250, categoryId, 10);

        String customer = register("olivia", "olivia@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, widget, 2);
        addToCart(customer, gadget, 1);

        JsonNode order = checkout(customer, addressId, null);
        assertThat(order.get("orderNumber").asText()).startsWith("NRY-");
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(order.get("paymentStatus").asText()).isEqualTo("UNPAID");
        assertThat(order.get("subtotal").asInt()).isEqualTo(450); // 2*100 + 1*250
        assertThat(order.get("total").asInt()).isEqualTo(450);
        assertThat(order.get("items")).hasSize(2);
        assertThat(order.get("shippingAddress").get("city").asText()).isEqualTo("Mumbai");

        // Cart is emptied by checkout.
        assertThat(getJson(customer, "/api/cart").get("items")).isEmpty();

        // Stock is reserved, not yet sold.
        JsonNode inv = getJson(admin, "/api/inventory/admin/product/" + widget);
        assertThat(inv.get("quantityReserved").asInt()).isEqualTo(2);
        assertThat(inv.get("availableQuantity").asInt()).isEqualTo(8);

        // Order shows up in history and by number.
        String orderNumber = order.get("orderNumber").asText();
        assertThat(getJson(customer, "/api/orders/me").get("totalElements").asInt()).isEqualTo(1);
        assertThat(getJson(customer, "/api/orders/me/" + orderNumber).get("orderNumber").asText())
                .isEqualTo(orderNumber);
    }

    @Test
    void orderLineItems_areImmutableSnapshots() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Snap Cat");
        long product = createProduct(admin, "Original Name", "ORD-SNAP", 100, categoryId, 10);

        String customer = register("simon", "simon@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 1);
        String orderNumber = checkout(customer, addressId, null).get("orderNumber").asText();

        // Admin renames and reprices the product after the order was placed.
        updateProduct(admin, product, "Renamed Later", "ORD-SNAP", 999, categoryId, 10);

        // The placed order still reflects the purchase-time name and price.
        JsonNode item = getJson(customer, "/api/orders/me/" + orderNumber).get("items").get(0);
        assertThat(item.get("productName").asText()).isEqualTo("Original Name");
        assertThat(item.get("unitPrice").asInt()).isEqualTo(100);
    }

    @Test
    void emptyCartCheckout_isRejected() {
        String customer = register("emma", "emma@example.com").getToken();
        long addressId = createAddress(customer);

        ResponseEntity<JsonNode> response = rest.exchange("/api/orders/checkout", HttpMethod.POST,
                bearer(customer, Map.of("addressId", addressId)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T46: concurrent checkout for the last unit ----

    @Test
    void concurrentCheckout_forLastUnit_onlyOneSucceeds() throws Exception {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Race Cat");
        long product = createProduct(admin, "Last One", "ORD-RACE", 100, categoryId, 1);

        String alice = register("racea", "racea@example.com").getToken();
        String bob = register("raceb", "raceb@example.com").getToken();
        long aliceAddr = createAddress(alice);
        long bobAddr = createAddress(bob);
        addToCart(alice, product, 1);
        addToCart(bob, product, 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> aliceCheckout = () -> rest.exchange("/api/orders/checkout", HttpMethod.POST,
                bearer(alice, Map.of("addressId", aliceAddr)), JsonNode.class).getStatusCode().value();
        Callable<Integer> bobCheckout = () -> rest.exchange("/api/orders/checkout", HttpMethod.POST,
                bearer(bob, Map.of("addressId", bobAddr)), JsonNode.class).getStatusCode().value();

        List<Future<Integer>> results = pool.invokeAll(List.of(aliceCheckout, bobCheckout));
        pool.shutdown();

        int created = 0;
        int conflict = 0;
        for (Future<Integer> f : results) {
            int status = f.get();
            if (status == 201) created++;
            else if (status == 409) conflict++;
        }
        assertThat(created).isEqualTo(1);
        assertThat(conflict).isEqualTo(1);

        // The single unit is reserved exactly once; nothing over-reserved.
        JsonNode inv = getJson(admin, "/api/inventory/admin/product/" + product);
        assertThat(inv.get("quantityReserved").asInt()).isEqualTo(1);
        assertThat(inv.get("availableQuantity").asInt()).isZero();
    }

    // ---- T47 / T49: cancellation releases stock ----

    @Test
    void cancel_releasesReservation() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Cancel Cat");
        long product = createProduct(admin, "Cancelable", "ORD-CAN", 100, categoryId, 5);

        String customer = register("carl", "carl@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 3);
        String orderNumber = checkout(customer, addressId, null).get("orderNumber").asText();

        assertThat(getJson(admin, "/api/inventory/admin/product/" + product)
                .get("availableQuantity").asInt()).isEqualTo(2);

        JsonNode cancelled = post(customer, "/api/orders/me/" + orderNumber + "/cancel", null);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");

        // Reservation is fully released.
        JsonNode inv = getJson(admin, "/api/inventory/admin/product/" + product);
        assertThat(inv.get("quantityReserved").asInt()).isZero();
        assertThat(inv.get("availableQuantity").asInt()).isEqualTo(5);
    }

    // ---- T48 / T49: admin status transitions + shipment converts stock to sold ----

    @Test
    void admin_advancesStatus_andShipmentSellsStock() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Ship Cat");
        long product = createProduct(admin, "Shippable", "ORD-SHIP", 100, categoryId, 5);

        String customer = register("nora", "nora@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 2);
        String orderNumber = checkout(customer, addressId, null).get("orderNumber").asText();

        setStatus(admin, orderNumber, "CONFIRMED", HttpStatus.OK);
        setStatus(admin, orderNumber, "PACKED", HttpStatus.OK);
        JsonNode shipped = setStatus(admin, orderNumber, "SHIPPED", HttpStatus.OK);
        assertThat(shipped.get("status").asText()).isEqualTo("SHIPPED");

        // Reserved -> sold; on-hand drops.
        JsonNode inv = getJson(admin, "/api/inventory/admin/product/" + product);
        assertThat(inv.get("quantityReserved").asInt()).isZero();
        assertThat(inv.get("quantitySold").asInt()).isEqualTo(2);
        assertThat(inv.get("quantityOnHand").asInt()).isEqualTo(3);
    }

    @Test
    void admin_illegalStatusTransition_isRejected() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Trans Cat");
        long product = createProduct(admin, "Transitionable", "ORD-TRN", 100, categoryId, 5);

        String customer = register("tina", "tina@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 1);
        String orderNumber = checkout(customer, addressId, null).get("orderNumber").asText();

        // PENDING -> DELIVERED skips the pipeline and must be refused.
        setStatus(admin, orderNumber, "DELIVERED", HttpStatus.CONFLICT);
    }

    // ---- T50: idempotent checkout ----

    @Test
    void checkout_withSameIdempotencyKey_returnsSameOrder_withoutDoubleReserving() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Idem Cat");
        long product = createProduct(admin, "Idempotent", "ORD-IDEM", 100, categoryId, 10);

        String customer = register("ivan", "ivan@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 2);

        String key = "checkout-key-123";
        JsonNode first = checkout(customer, addressId, key);
        String orderNumber = first.get("orderNumber").asText();

        // Re-add to cart and replay the same key: must return the original order, not a new one.
        addToCart(customer, product, 2);
        JsonNode replay = checkout(customer, addressId, key);
        assertThat(replay.get("orderNumber").asText()).isEqualTo(orderNumber);

        // Only the first checkout reserved stock (2), the replay reserved nothing more.
        assertThat(getJson(admin, "/api/inventory/admin/product/" + product)
                .get("quantityReserved").asInt()).isEqualTo(2);
        assertThat(getJson(customer, "/api/orders/me").get("totalElements").asInt()).isEqualTo(1);
    }

    // ---- isolation ----

    @Test
    void order_isNotVisibleToAnotherCustomer() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Order Iso Cat");
        long product = createProduct(admin, "Private", "ORD-ISO", 100, categoryId, 10);

        String owner = register("owena", "owena@example.com").getToken();
        long addressId = createAddress(owner);
        addToCart(owner, product, 1);
        String orderNumber = checkout(owner, addressId, null).get("orderNumber").asText();

        String other = register("otherb", "otherb@example.com").getToken();
        ResponseEntity<JsonNode> peek = rest.exchange("/api/orders/me/" + orderNumber, HttpMethod.GET,
                bearer(other), JsonNode.class);
        assertThat(peek.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
        ResponseEntity<JsonNode> response = rest.exchange("/api/products/admin", HttpMethod.POST,
                bearer(token, productBody(name, sku, sellingPrice, categoryId, quantity)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    private void updateProduct(String token, long id, String name, String sku, int sellingPrice, long categoryId, int quantity) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/products/admin/" + id, HttpMethod.PUT,
                bearer(token, productBody(name, sku, sellingPrice, categoryId, quantity)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> productBody(String name, String sku, int sellingPrice, long categoryId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("sku", sku);
        body.put("price", sellingPrice + 10);
        body.put("sellingPrice", sellingPrice);
        body.put("categoryId", categoryId);
        body.put("quantity", quantity);
        return body;
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

    private JsonNode checkout(String token, long addressId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("addressId", addressId), headers);
        ResponseEntity<JsonNode> response = rest.exchange("/api/orders/checkout", HttpMethod.POST, entity, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private JsonNode setStatus(String token, String orderNumber, String status, HttpStatus expected) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/orders/admin/" + orderNumber + "/status",
                HttpMethod.PUT, bearer(token, Map.of("status", status)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
        return response.getBody();
    }

    private JsonNode post(String token, String path, Map<String, Object> body) {
        HttpEntity<?> entity = body == null ? bearer(token) : bearer(token, body);
        ResponseEntity<JsonNode> response = rest.exchange(path, HttpMethod.POST, entity, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
