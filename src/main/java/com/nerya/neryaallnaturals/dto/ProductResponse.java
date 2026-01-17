package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.entity.ProductImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private String shortDescription;
    private String longDescription;
    private BigDecimal price;
    private BigDecimal sellingPrice;
    private Integer discountPercentage;
    private String brand;
    private String weight;
    private Boolean inStock;
    private Integer quantity;
    private Integer minQuantity;
    private Boolean isActive;
    private Boolean isFeatured;
    private BigDecimal averageRating;
    private Integer totalReviews;
    private Set<String> tags;
    private String metaTitle;
    private String metaDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Category info
    private Long categoryId;
    private String categoryName;
    
    // Images
    private List<ProductImageResponse> images;
    private String primaryImageUrl;

    public static ProductResponse fromEntity(Product product) {
        ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .shortDescription(product.getShortDescription())
                .longDescription(product.getLongDescription())
                .price(product.getPrice())
                .sellingPrice(product.getSellingPrice())
                .discountPercentage(product.getDiscountPercentage())
                .brand(product.getBrand())
                .weight(product.getWeight())
                .inStock(product.getInStock())
                .quantity(product.getQuantity())
                .minQuantity(product.getMinQuantity())
                .isActive(product.getIsActive())
                .isFeatured(product.getIsFeatured())
                .averageRating(product.getAverageRating())
                .totalReviews(product.getTotalReviews())
                .tags(product.getTags())
                .metaTitle(product.getMetaTitle())
                .metaDescription(product.getMetaDescription())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt());

        // Set category info if available
        if (product.getCategory() != null) {
            builder.categoryId(product.getCategory().getId())
                   .categoryName(product.getCategory().getName());
        }

        // Set images
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            List<ProductImageResponse> imageResponses = product.getImages().stream()
                    .map(ProductImageResponse::fromEntity)
                    .collect(Collectors.toList());
            builder.images(imageResponses);

            // Find primary image
            String primaryUrl = product.getImages().stream()
                    .filter(ProductImage::getIsPrimary)
                    .findFirst()
                    .map(ProductImage::getImageUrl)
                    .orElse(product.getImages().get(0).getImageUrl());
            builder.primaryImageUrl(primaryUrl);
        }

        return builder.build();
    }
}

