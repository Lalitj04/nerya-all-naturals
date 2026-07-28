package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.MediaAssetResponse;
import com.nerya.neryaallnaturals.dto.PagedResponse;
import com.nerya.neryaallnaturals.dto.ProductRequest;
import com.nerya.neryaallnaturals.dto.ProductResponse;
import com.nerya.neryaallnaturals.entity.Category;
import com.nerya.neryaallnaturals.entity.MediaAsset;
import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.entity.ProductImage;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.CategoryRepository;
import com.nerya.neryaallnaturals.repository.MediaAssetRepository;
import com.nerya.neryaallnaturals.repository.ProductImageRepository;
import com.nerya.neryaallnaturals.repository.ProductRepository;
import com.nerya.neryaallnaturals.repository.spec.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final InventoryService inventoryService;

    /**
     * Public product search with optional keyword/category/price/stock filters and
     * pagination (T35). Only active products are ever returned.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> searchActiveProducts(String q, Long categoryId,
                                                               BigDecimal minPrice, BigDecimal maxPrice,
                                                               Boolean inStock, Pageable pageable) {
        log.debug("Searching products: q={}, categoryId={}, minPrice={}, maxPrice={}, inStock={}, pageable={}",
                q, categoryId, minPrice, maxPrice, inStock, pageable);
        Specification<Product> spec = ProductSpecifications.activeMatching(q, categoryId, minPrice, maxPrice, inStock);
        Page<Product> page = productRepository.findAll(spec, pageable);
        return PagedResponse.of(toResponses(page.getContent()), page);
    }

    /**
     * Get product by ID
     */
    @Transactional(readOnly = true)
    public Optional<ProductResponse> getProductById(Long id) {
        log.debug("Fetching product with ID: {}", id);
        return productRepository.findById(id)
                .map(product -> {
                    ProductResponse response = ProductResponse.fromEntity(product, mediaResponsesFor(product.getId()));
                    response.setQuantity(inventoryService.getAvailableQuantity(product.getId()));
                    return response;
                });
    }

    /**
     * Get products by category ID
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        log.debug("Fetching products for category ID: {}", categoryId);
        return toResponses(productRepository.findByCategoryIdAndActive(categoryId));
    }

    /**
     * Get all products (admin only)
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        log.debug("Fetching all products");
        return toResponses(productRepository.findAll());
    }

    /**
     * Map a list of products to responses, batch-fetching their media assets in one
     * query instead of one query per product.
     */
    private List<ProductResponse> toResponses(List<Product> products) {
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, List<MediaAssetResponse>> mediaByProduct = mediaAssetRepository
                .findByProductIdInAndIsActiveTrue(productIds).stream()
                .map(MediaAssetResponse::fromEntity)
                .collect(Collectors.groupingBy(MediaAssetResponse::getProductId));
        Map<Long, Integer> availableByProduct = inventoryService.getAvailableQuantitiesByProductId(productIds);

        return products.stream()
                .map(product -> {
                    ProductResponse response = ProductResponse.fromEntity(product,
                            mediaByProduct.getOrDefault(product.getId(), Collections.emptyList()));
                    response.setQuantity(availableByProduct.getOrDefault(product.getId(), 0));
                    return response;
                })
                .collect(Collectors.toList());
    }

    private List<MediaAssetResponse> mediaResponsesFor(Long productId) {
        return mediaAssetRepository.findByProductIdAndIsActiveTrue(productId).stream()
                .map(MediaAssetResponse::fromEntity)
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
            throw new ConflictException("SKU already exists: " + productRequest.getSku());
        }

        // Get category
        Optional<Category> categoryOptional = categoryRepository.findById(productRequest.getCategoryId());
        if (categoryOptional.isEmpty()) {
            throw new ResourceNotFoundException("Category not found with ID: " + productRequest.getCategoryId());
        }

        // Create product. Stock (inStock/quantity) is intentionally NOT set from the
        // request here — Inventory is the single source of truth (T36); inStock is derived
        // from the inventory row created below.
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
        attachMediaAssets(savedProduct, productRequest.getMediaAssetIds());

        // Every product gets exactly one inventory row, seeded from the request's quantity
        // (or 0). This also syncs the product's inStock flag (T36/T37).
        inventoryService.createForProduct(savedProduct, productRequest.getQuantity());

        log.info("Product created successfully: {}", savedProduct.getName());
        ProductResponse response = ProductResponse.fromEntity(savedProduct, mediaResponsesFor(savedProduct.getId()));
        response.setQuantity(inventoryService.getAvailableQuantity(savedProduct.getId()));
        return response;
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
                throw new ConflictException("SKU already exists: " + productRequest.getSku());
            }
        }

        // Get category
        Optional<Category> categoryOptional = categoryRepository.findById(productRequest.getCategoryId());
        if (categoryOptional.isEmpty()) {
            throw new ResourceNotFoundException("Category not found with ID: " + productRequest.getCategoryId());
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
        // inStock/quantity are owned by Inventory (T36) — not overwritten from the request.
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
        attachMediaAssets(updatedProduct, productRequest.getMediaAssetIds());
        log.info("Product updated successfully: {}", updatedProduct.getName());

        ProductResponse response = ProductResponse.fromEntity(updatedProduct, mediaResponsesFor(updatedProduct.getId()));
        response.setQuantity(inventoryService.getAvailableQuantity(updatedProduct.getId()));
        return Optional.of(response);
    }

    /**
     * Attach already-uploaded media assets to a product by ID, reassigning any that belonged
     * to a different product. Silently ignores IDs that don't exist.
     */
    private void attachMediaAssets(Product product, List<Long> mediaAssetIds) {
        if (mediaAssetIds == null || mediaAssetIds.isEmpty()) {
            return;
        }

        List<MediaAsset> assets = mediaAssetRepository.findAllById(mediaAssetIds);
        assets.forEach(asset -> asset.setProduct(product));
        mediaAssetRepository.saveAll(assets);
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

