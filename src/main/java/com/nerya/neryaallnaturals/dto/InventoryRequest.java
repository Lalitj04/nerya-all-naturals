package com.nerya.neryaallnaturals.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity on hand is required")
    @Min(value = 0, message = "Quantity on hand cannot be negative")
    private Integer quantityOnHand;

    @Min(value = 0, message = "Quantity reserved cannot be negative")
    private Integer quantityReserved = 0;

    @Min(value = 0, message = "Quantity sold cannot be negative")
    private Integer quantitySold = 0;

    @Min(value = 0, message = "Min stock level cannot be negative")
    private Integer minStockLevel = 5;

    @Min(value = 0, message = "Max stock level cannot be negative")
    private Integer maxStockLevel = 1000;

    @Min(value = 0, message = "Reorder quantity cannot be negative")
    private Integer reorderQuantity = 50;

    private String lastUpdatedBy;
}
