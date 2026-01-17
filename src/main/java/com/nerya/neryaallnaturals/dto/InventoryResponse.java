package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.Inventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantityOnHand;
    private Integer quantityReserved;
    private Integer quantitySold;
    private Integer availableQuantity;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Integer reorderQuantity;
    private Boolean isLowStock;
    private String lastUpdatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InventoryResponse fromEntity(Inventory inventory) {
        InventoryResponse.InventoryResponseBuilder builder = InventoryResponse.builder()
                .id(inventory.getId())
                .quantityOnHand(inventory.getQuantityOnHand())
                .quantityReserved(inventory.getQuantityReserved())
                .quantitySold(inventory.getQuantitySold())
                .availableQuantity(inventory.getAvailableQuantity())
                .minStockLevel(inventory.getMinStockLevel())
                .maxStockLevel(inventory.getMaxStockLevel())
                .reorderQuantity(inventory.getReorderQuantity())
                .isLowStock(inventory.isLowStock())
                .lastUpdatedBy(inventory.getLastUpdatedBy())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt());

        // Set product info if available
        if (inventory.getProduct() != null) {
            builder.productId(inventory.getProduct().getId())
                   .productName(inventory.getProduct().getName())
                   .productSku(inventory.getProduct().getSku());
        }

        return builder.build();
    }
}
