package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.dto.PagedResponse;
import com.nerya.neryaallnaturals.dto.ProductRequest;
import com.nerya.neryaallnaturals.dto.ProductResponse;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;

    /**
     * Search active products with optional filters and pagination.
     * Open API - No authentication required
     *
     * @param q          keyword matched against name/short/long description (optional)
     * @param categoryId restrict to a category (optional)
     * @param minPrice   minimum selling price (optional)
     * @param maxPrice   maximum selling price (optional)
     * @param inStock    restrict to in-stock (true) or out-of-stock (false) products (optional)
     * @param pageable   page/size/sort (default size 20, max 100 via application.yml)
     * @return a page of matching products
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getProducts(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "inStock", required = false) Boolean inStock,
            Pageable pageable) {
        log.info("Searching products (q={}, categoryId={})", q, categoryId);
        return ResponseEntity.ok(
                productService.searchActiveProducts(q, categoryId, minPrice, maxPrice, inStock, pageable));
    }

    /**
     * Fetch products by category
     * Open API - No authentication required
     * 
     * @param categoryId category ID
     * @return list of products in the specified category
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId) {
        log.info("Fetching products for category ID: {}", categoryId);
        List<ProductResponse> products = productService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(products);
    }

    /**
     * Fetch product by ID
     * Open API - No authentication required
     * 
     * @param id product ID
     * @return product details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        log.info("Fetching product with ID: {}", id);
        ProductResponse product = productService.getProductById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        return ResponseEntity.ok(product);
    }

    /**
     * Admin only - Get all products including inactive
     * Admin API - Requires authentication
     * 
     * @return list of all products
     */
    @GetMapping("/admin/all")
    @AdminOnly
    public ResponseEntity<List<ProductResponse>> getAllProductsAdmin() {
        log.info("Admin: Fetching all products");
        List<ProductResponse> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * Admin only - Create a new product
     * Admin API - Requires authentication
     * 
     * @param productRequest product details
     * @return created product
     */
    @PostMapping("/admin")
    @AdminOnly
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        log.info("Admin: Creating new product: {}", productRequest.getName());
        ProductResponse createdProduct = productService.createProduct(productRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    /**
     * Admin only - Update product details
     * Admin API - Requires authentication
     * 
     * @param id product ID
     * @param productRequest updated product details
     * @return updated product
     */
    @PutMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest productRequest) {
        log.info("Admin: Updating product with ID: {}", id);
        ProductResponse updatedProduct = productService.updateProduct(id, productRequest)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        return ResponseEntity.ok(updatedProduct);
    }

    /**
     * Admin only - Delete product
     * Admin API - Requires authentication
     * 
     * @param id product ID
     * @return success message
     */
    @DeleteMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("Admin: Deleting product with ID: {}", id);

        boolean deleted = productService.deleteProduct(id);

        if (!deleted) {
            throw new ResourceNotFoundException("Product not found with ID: " + id);
        }

        return ResponseEntity.noContent().build();
    }
}

