package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.ProductRequest;
import com.nerya.neryaallnaturals.dto.ProductResponse;
import com.nerya.neryaallnaturals.entity.Category;
import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.entity.ProductImage;
import com.nerya.neryaallnaturals.repository.CategoryRepository;
import com.nerya.neryaallnaturals.repository.InventoryRepository;
import com.nerya.neryaallnaturals.repository.ProductImageRepository;
import com.nerya.neryaallnaturals.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryRepository inventoryRepository;

    /**
     * Get all active products
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllActiveProducts() {
        log.debug("Fetching all active products");
        return productRepository.findByIsActiveTrue().stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get product by ID
     */
    @Transactional(readOnly = true)
    public Optional<ProductResponse> getProductById(Long id) {
        log.debug("Fetching product with ID: {}", id);
        return productRepository.findById(id)
                .map(ProductResponse::fromEntity);
    }

    /**
     * Get products by category ID
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        log.debug("Fetching products for category ID: {}", categoryId);
        return productRepository.findByCategoryIdAndActive(categoryId).stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get all products (admin only)
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        log.debug("Fetching all products");
        return productRepository.findAll().stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Create a new product (admin only)
     */
    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest) {
        log.info("Creating new product: {}", productRequest.getName());

        // Check if SKU already exists
        if (productRepository.findBySku(productRequest.getSku()).isPresent()) {
            throw new RuntimeException("SKU already exists: " + productRequest.getSku());
        }

        // Get category
        Optional<Category> categoryOptional = categoryRepository.findById(productRequest.getCategoryId());
        if (categoryOptional.isEmpty()) {
            throw new RuntimeException("Category not found with ID: " + productRequest.getCategoryId());
        }

        // Create product
        Product product = Product.builder()
                .name(productRequest.getName())
                .sku(productRequest.getSku())
                .shortDescription(productRequest.getShortDescription())
                .longDescription(productRequest.getLongDescription())
                .price(productRequest.getPrice())
                .sellingPrice(productRequest.getSellingPrice())
                .discountPercentage(productRequest.getDiscountPercentage())
                .brand(productRequest.getBrand())
                .weight(productRequest.getWeight())
                .inStock(productRequest.getInStock())
                .quantity(productRequest.getQuantity())
                .minQuantity(productRequest.getMinQuantity())
                .isActive(productRequest.getIsActive())
                .isFeatured(productRequest.getIsFeatured())
                .tags(productRequest.getTags())
                .metaTitle(productRequest.getMetaTitle())
                .metaDescription(productRequest.getMetaDescription())
                .category(categoryOptional.get())
                .build();

        // Add images if provided
        if (productRequest.getImageUrls() != null && !productRequest.getImageUrls().isEmpty()) {
            int order = 0;
            for (String imageUrl : productRequest.getImageUrls()) {
                ProductImage image = ProductImage.builder()
                        .product(product)
                        .imageUrl(imageUrl)
                        .isPrimary(order == 0 && (productRequest.getIsPrimaryImage() == null || productRequest.getIsPrimaryImage()))
                        .displayOrder(order++)
                        .build();
                product.getImages().add(image);
            }
        }

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully: {}", savedProduct.getName());

        return ProductResponse.fromEntity(savedProduct);
    }

    /**
     * Update product by ID (admin only)
     */
    @Transactional
    public Optional<ProductResponse> updateProduct(Long id, ProductRequest productRequest) {
        log.info("Updating product with ID: {}", id);
        
        Optional<Product> productOptional = productRepository.findById(id);
        if (productOptional.isEmpty()) {
            return Optional.empty();
        }

        Product product = productOptional.get();
        
        // Check if SKU is being changed and if it exists
        if (!product.getSku().equals(productRequest.getSku())) {
            if (productRepository.findBySku(productRequest.getSku()).isPresent()) {
                throw new RuntimeException("SKU already exists: " + productRequest.getSku());
            }
        }

        // Get category
        Optional<Category> categoryOptional = categoryRepository.findById(productRequest.getCategoryId());
        if (categoryOptional.isEmpty()) {
            throw new RuntimeException("Category not found with ID: " + productRequest.getCategoryId());
        }

        // Update product fields
        product.setName(productRequest.getName());
        product.setSku(productRequest.getSku());
        product.setShortDescription(productRequest.getShortDescription());
        product.setLongDescription(productRequest.getLongDescription());
        product.setPrice(productRequest.getPrice());
        product.setSellingPrice(productRequest.getSellingPrice());
        product.setDiscountPercentage(productRequest.getDiscountPercentage());
        product.setBrand(productRequest.getBrand());
        product.setWeight(productRequest.getWeight());
        product.setInStock(productRequest.getInStock());
        product.setQuantity(productRequest.getQuantity());
        product.setMinQuantity(productRequest.getMinQuantity());
        product.setIsActive(productRequest.getIsActive());
        product.setIsFeatured(productRequest.getIsFeatured());
        product.setTags(productRequest.getTags());
        product.setMetaTitle(productRequest.getMetaTitle());
        product.setMetaDescription(productRequest.getMetaDescription());
        product.setCategory(categoryOptional.get());

        // Update images if provided
        if (productRequest.getImageUrls() != null && !productRequest.getImageUrls().isEmpty()) {
            // Remove existing images
            product.getImages().clear();
            
            // Add new images
            int order = 0;
            for (String imageUrl : productRequest.getImageUrls()) {
                ProductImage image = ProductImage.builder()
                        .product(product)
                        .imageUrl(imageUrl)
                        .isPrimary(order == 0 && (productRequest.getIsPrimaryImage() == null || productRequest.getIsPrimaryImage()))
                        .displayOrder(order++)
                        .build();
                product.getImages().add(image);
            }
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully: {}", updatedProduct.getName());
        
        return Optional.of(ProductResponse.fromEntity(updatedProduct));
    }

    /**
     * Delete product by ID (admin only)
     */
    @Transactional
    public boolean deleteProduct(Long id) {
        log.info("Deleting product with ID: {}", id);
        
        Optional<Product> productOptional = productRepository.findById(id);
        if (productOptional.isEmpty()) {
            return false;
        }

        // Soft delete - set isActive to false
        Product product = productOptional.get();
        product.setIsActive(false);
        productRepository.save(product);
        
        log.info("Product soft deleted successfully with ID: {}", id);
        return true;
    }
}

