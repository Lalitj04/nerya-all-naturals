package com.nerya.neryaallnaturals.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.nerya.neryaallnaturals.dto.LoginRequest;
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

class CatalogIntegrationTest extends AbstractIntegrationTest {

    // ---- T35: public search / filter / pagination ----

    @Test
    void productSearch_isPublic_andPaginates() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Search Cat A");
        for (int i = 1; i <= 5; i++) {
            createProduct(admin, "Widget " + i + " special", "WSEARCH-" + i, i * 40, categoryId, i);
        }

        // No Authorization header — search must be public.
        ResponseEntity<JsonNode> page0 = rest.getForEntity("/api/products?q=special&size=2&page=0", JsonNode.class);

        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = page0.getBody();
        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(2);
        assertThat(body.get("totalElements").asLong()).isEqualTo(5);
        assertThat(body.get("totalPages").asInt()).isEqualTo(3);
        assertThat(body.get("content")).hasSize(2);
        assertThat(body.get("last").asBoolean()).isFalse();
    }

    @Test
    void productSearch_combinesCategoryAndPriceFilters() {
        String admin = adminToken();
        long catA = createCategory(admin, "Filter Cat A");
        long catB = createCategory(admin, "Filter Cat B");
        createProduct(admin, "Cheap A", "F-A1", 50, catA, 3);
        createProduct(admin, "Pricey A", "F-A2", 200, catA, 3);
        createProduct(admin, "Pricey B", "F-B1", 200, catB, 3);

        // categoryId=catA AND minPrice=100 -> only "Pricey A".
        ResponseEntity<JsonNode> result = rest.getForEntity(
                "/api/products?categoryId=" + catA + "&minPrice=100", JsonNode.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("totalElements").asLong()).isEqualTo(1);
        assertThat(result.getBody().get("content").get(0).get("name").asText()).isEqualTo("Pricey A");
    }

    @Test
    void productSearch_pageSizeIsClampedToMax() {
        ResponseEntity<JsonNode> result = rest.getForEntity("/api/products?size=500", JsonNode.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("size").asInt()).isEqualTo(100);
    }

    // ---- T36 / T37: inventory is the source of truth for stock ----

    @Test
    void createProduct_autoCreatesInventory_andDerivesInStock() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Stock Cat");

        long inStockProduct = createProduct(admin, "Has Stock", "STK-1", 100, categoryId, 7);
        long zeroStockProduct = createProduct(admin, "No Stock", "STK-0", 100, categoryId, 0);

        // Inventory row was auto-created with the requested quantity.
        JsonNode inv = getJson(admin, "/api/inventory/admin/product/" + inStockProduct);
        assertThat(inv.get("quantityOnHand").asInt()).isEqualTo(7);
        assertThat(inv.get("availableQuantity").asInt()).isEqualTo(7);

        // inStock is derived from inventory, not the request.
        assertThat(getJson(null, "/api/products/" + inStockProduct).get("inStock").asBoolean()).isTrue();
        assertThat(getJson(null, "/api/products/" + zeroStockProduct).get("inStock").asBoolean()).isFalse();
    }

    @Test
    void drainingInventory_flipsProductInStockInPublicApi() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Drain Cat");
        long productId = createProduct(admin, "Drainable", "DRN-1", 100, categoryId, 10);

        assertThat(getJson(null, "/api/products/" + productId).get("inStock").asBoolean()).isTrue();

        long inventoryId = getJson(admin, "/api/inventory/admin/product/" + productId).get("id").asLong();
        Map<String, Object> drain = new LinkedHashMap<>();
        drain.put("productId", productId);
        drain.put("quantityOnHand", 0);
        ResponseEntity<JsonNode> update = rest.exchange("/api/inventory/admin/" + inventoryId, HttpMethod.PUT,
                bearer(admin, drain), JsonNode.class);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(getJson(null, "/api/products/" + productId).get("inStock").asBoolean()).isFalse();
    }

    // ---- T34: category update / soft delete ----

    @Test
    void category_canBeRenamed_andSoftDeletedWhenEmpty() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Renamable");

        Map<String, Object> rename = Map.of("name", "Renamed", "isActive", true);
        ResponseEntity<JsonNode> updated = rest.exchange("/api/categories/admin/" + categoryId, HttpMethod.PUT,
                bearer(admin, rename), JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("name").asText()).isEqualTo("Renamed");

        // Empty category deletes cleanly and drops out of the active list.
        ResponseEntity<Void> deleted = rest.exchange("/api/categories/admin/" + categoryId, HttpMethod.DELETE,
                bearer(admin), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode active = getJson(null, "/api/categories");
        boolean stillListed = false;
        for (JsonNode c : active) {
            if (c.get("id").asLong() == categoryId) {
                stillListed = true;
            }
        }
        assertThat(stillListed).isFalse();
    }

    @Test
    void category_deleteRefusedWhenProductsExist() {
        String admin = adminToken();
        long categoryId = createCategory(admin, "Has Products");
        createProduct(admin, "Blocking Product", "BLK-1", 100, categoryId, 1);

        ResponseEntity<JsonNode> deleted = rest.exchange("/api/categories/admin/" + categoryId, HttpMethod.DELETE,
                bearer(admin), JsonNode.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deleted.getBody().get("code").asText()).isEqualTo("CONFLICT");
    }

    // ---- security: admin catalog writes reject anonymous callers ----

    @Test
    void adminCatalogWrites_rejectUnauthenticated() {
        ResponseEntity<JsonNode> category = rest.exchange("/api/categories/admin", HttpMethod.POST,
                json(Map.of("name", "Nope")), JsonNode.class);
        assertThat(category.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> product = rest.exchange("/api/products/admin", HttpMethod.POST,
                json(Map.of("name", "Nope", "sku", "NOPE", "price", 1, "sellingPrice", 1, "categoryId", 1)),
                JsonNode.class);
        assertThat(product.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ---- helpers ----

    private String adminToken() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/login",
                new LoginRequest("testadmin", "AdminPass123!"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("token").asText();
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

    private JsonNode getJson(String token, String path) {
        ResponseEntity<JsonNode> response = (token == null)
                ? rest.getForEntity(path, JsonNode.class)
                : rest.exchange(path, HttpMethod.GET, bearer(token), JsonNode.class);
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

    private <T> HttpEntity<T> json(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
