package com.nerya.neryaallnaturals.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.nerya.neryaallnaturals.dto.AuthResponse;
import com.nerya.neryaallnaturals.dto.LoginRequest;
import com.nerya.neryaallnaturals.dto.RegisterRequest;
import com.nerya.neryaallnaturals.entity.Order;
import com.nerya.neryaallnaturals.entity.Payment;
import com.nerya.neryaallnaturals.entity.PaymentProvider;
import com.nerya.neryaallnaturals.entity.PaymentTxnStatus;
import com.nerya.neryaallnaturals.repository.OrderRepository;
import com.nerya.neryaallnaturals.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** T54/T55: COD flow, gateway-not-configured handling, webhook signature verification + idempotency. */
class PaymentIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @DynamicPropertySource
    static void razorpayProps(DynamicPropertyRegistry registry) {
        registry.add("payment.razorpay.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @Test
    void codPayment_marksOrderCodAndConfirmed() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Pay Cat");
        long product = createProduct(admin, "Payable", "PAY-COD", 100, categoryId, 5);

        String customer = register("paula", "paula@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 1);
        String orderNumber = checkout(customer, addressId).get("orderNumber").asText();

        JsonNode payment = createPayment(customer, orderNumber, "COD", HttpStatus.CREATED);
        assertThat(payment.get("status").asText()).isEqualTo("SUCCESS");

        JsonNode order = getJson(customer, "/api/orders/me/" + orderNumber);
        assertThat(order.get("paymentStatus").asText()).isEqualTo("COD");
        assertThat(order.get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void payment_forAlreadyPaidOrder_isRejected() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Pay Cat 2");
        long product = createProduct(admin, "PayTwice", "PAY-DUP", 100, categoryId, 5);

        String customer = register("perry", "perry@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 1);
        String orderNumber = checkout(customer, addressId).get("orderNumber").asText();

        createPayment(customer, orderNumber, "COD", HttpStatus.CREATED);
        createPayment(customer, orderNumber, "COD", HttpStatus.CONFLICT);
    }

    @Test
    void razorpayPayment_withoutGatewayConfigured_isRejected() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Pay Cat 3");
        long product = createProduct(admin, "PayGateway", "PAY-RZP", 100, categoryId, 5);

        String customer = register("gwen", "gwen@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 1);
        String orderNumber = checkout(customer, addressId).get("orderNumber").asText();

        // No RAZORPAY_KEY_ID configured in this test environment.
        createPayment(customer, orderNumber, "RAZORPAY", HttpStatus.CONFLICT);
    }

    @Test
    void webhook_tamperedSignature_isRejected() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Webhook Cat");
        long product = createProduct(admin, "Webhookable", "PAY-WH", 100, categoryId, 5);
        String customer = register("wendy", "wendy@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 1);
        String orderNumber = checkout(customer, addressId).get("orderNumber").asText();

        Payment payment = seedGatewayPayment(orderNumber, "order_rzp_tampered");
        String body = webhookBody("payment.captured", payment.getProviderOrderId(), "pay_tampered");

        ResponseEntity<JsonNode> response = postWebhook(body, "not-the-real-signature");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void webhook_validSignature_marksOrderPaid_andIgnoresReplay() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Webhook Cat 2");
        long product = createProduct(admin, "WebhookPaid", "PAY-WH2", 100, categoryId, 5);
        String customer = register("wynn", "wynn@example.com").getToken();
        long addressId = createAddress(customer);
        addToCart(customer, product, 1);
        String orderNumber = checkout(customer, addressId).get("orderNumber").asText();

        Payment payment = seedGatewayPayment(orderNumber, "order_rzp_" + orderNumber);
        String body = webhookBody("payment.captured", payment.getProviderOrderId(), "pay_" + orderNumber);
        String signature = sign(body);

        ResponseEntity<JsonNode> first = postWebhook(body, signature);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = getJson(customer, "/api/orders/me/" + orderNumber);
        assertThat(order.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(order.get("status").asText()).isEqualTo("CONFIRMED");

        // Duplicate delivery of the same webhook is a no-op, not a double-apply.
        ResponseEntity<JsonNode> replay = postWebhook(body, signature);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getJson(customer, "/api/orders/me/" + orderNumber).get("paymentStatus").asText())
                .isEqualTo("PAID");
    }

    // ---- helpers ----

    private Payment seedGatewayPayment(String orderNumber, String providerOrderId) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        Payment payment = Payment.builder()
                .order(order)
                .provider(PaymentProvider.RAZORPAY)
                .providerOrderId(providerOrderId)
                .amount(order.getTotal())
                .status(PaymentTxnStatus.CREATED)
                .build();
        return paymentRepository.save(payment);
    }

    private String webhookBody(String event, String providerOrderId, String paymentId) {
        return "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + paymentId + "\",\"order_id\":\"" + providerOrderId + "\"}}}}";
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity<JsonNode> postWebhook(String body, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (signature != null) {
            headers.set("X-Razorpay-Signature", signature);
        }
        return rest.exchange("/api/payments/webhook", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode createPayment(String token, String orderNumber, String provider, HttpStatus expected) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/payments/create", HttpMethod.POST,
                bearer(token, Map.of("orderNumber", orderNumber, "provider", provider)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
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
