package com.nerya.neryaallnaturals.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;

    private String shortDescription;

    private String longDescription;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal sellingPrice;

    @Min(0)
    @Max(100)
    private Integer discountPercentage;

    private String brand;

    private String weight;

    private Boolean inStock = true;

    @Min(0)
    private Integer quantity;

    @Min(0)
    private Integer minQuantity;

    private Boolean isActive = true;

    private Boolean isFeatured = false;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private Set<String> tags;

    private String metaTitle;

    private String metaDescription;

    private List<String> imageUrls;

    private Boolean isPrimaryImage;
}

