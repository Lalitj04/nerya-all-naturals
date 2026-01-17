package com.nerya.neryaallnaturals.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @NotBlank(message = "Product name is required")
    @Size(max = 200)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Size(max = 2000)
    @Column(name = "short_description", length = 2000)
    private String shortDescription;

    @Column(name = "long_description", columnDefinition = "TEXT")
    private String longDescription;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "selling_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "discount_percentage")
    @Min(0)
    @Max(100)
    private Integer discountPercentage;

    @Size(max = 100)
    @Column(name = "brand")
    private String brand;

    @Size(max = 50)
    @Column(name = "weight")
    private String weight; // e.g., "250g", "500ml"

    @Column(name = "in_stock")
    @Builder.Default
    private Boolean inStock = true;

    @Column(name = "quantity")
    @Min(0)
    private Integer quantity;

    @Column(name = "min_quantity")
    @Min(0)
    private Integer minQuantity; // reorder level

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_featured")
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "rating")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "5.0")
    private BigDecimal averageRating;

    @Column(name = "total_reviews")
    private Integer totalReviews = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ProductReview> reviews = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @Size(max = 255)
    @Column(name = "meta_title")
    private String metaTitle;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;
}

