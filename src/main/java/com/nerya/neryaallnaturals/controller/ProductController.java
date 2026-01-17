package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.dto.ProductRequest;
import com.nerya.neryaallnaturals.dto.ProductResponse;
import com.nerya.neryaallnaturals.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;

    /**
     * Fetch all active products
     * Open API - No authentication required
     * 
     * @return list of all active products
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        log.info("Fetching all active products");
        List<ProductResponse> products = productService.getAllActiveProducts();
        return ResponseEntity.ok(products);
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
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        log.info("Fetching product with ID: {}", id);
        Optional<ProductResponse> product = productService.getProductById(id);
        
        if (product.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product not found with ID: " + id);
        }
        
        return ResponseEntity.ok(product.get());
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
    public ResponseEntity<?> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        log.info("Admin: Creating new product: {}", productRequest.getName());
        
        try {
            ProductResponse createdProduct = productService.createProduct(productRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
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
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest productRequest) {
        log.info("Admin: Updating product with ID: {}", id);
        
        try {
            Optional<ProductResponse> updatedProduct = productService.updateProduct(id, productRequest);
            
            if (updatedProduct.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Product not found with ID: " + id);
            }
            
            return ResponseEntity.ok(updatedProduct.get());
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
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
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        log.info("Admin: Deleting product with ID: {}", id);
        
        boolean deleted = productService.deleteProduct(id);
        
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product not found with ID: " + id);
        }
        
        return ResponseEntity.ok("Product deleted successfully");
    }
}

