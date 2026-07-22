package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.MediaAsset;
import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.entity.ProductImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
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

    // Images — Drive-backed media_assets rows when present, otherwise the legacy
    // ProductImage rows adapted into the same shape (see [[T25]] media migration).
    private List<MediaAssetResponse> images;
    private String primaryImageUrl;

    public static ProductResponse fromEntity(Product product) {
        return fromEntity(product, Collections.emptyList());
    }

    public static ProductResponse fromEntity(Product product, List<MediaAssetResponse> mediaAssets) {
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

        List<MediaAssetResponse> images = (mediaAssets != null && !mediaAssets.isEmpty())
                ? mediaAssets
                : legacyImagesAsMediaAssets(product);

        if (!images.isEmpty()) {
            builder.images(images).primaryImageUrl(images.get(0).getPublicUrl());
        }

        return builder.build();
    }

    private static List<MediaAssetResponse> legacyImagesAsMediaAssets(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductImage> ordered = product.getImages().stream()
                .sorted((a, b) -> {
                    if (Boolean.TRUE.equals(a.getIsPrimary()) && !Boolean.TRUE.equals(b.getIsPrimary())) {
                        return -1;
                    }
                    if (!Boolean.TRUE.equals(a.getIsPrimary()) && Boolean.TRUE.equals(b.getIsPrimary())) {
                        return 1;
                    }
                    return a.getDisplayOrder().compareTo(b.getDisplayOrder());
                })
                .collect(Collectors.toList());

        return ordered.stream()
                .map(image -> MediaAssetResponse.builder()
                        .id(image.getId())
                        .category(MediaAsset.MediaCategory.PRODUCT)
                        .publicUrl(image.getImageUrl())
                        .altText(image.getAltText())
                        .sortOrder(image.getDisplayOrder())
                        .productId(product.getId())
                        .build())
                .collect(Collectors.toList());
    }
}
