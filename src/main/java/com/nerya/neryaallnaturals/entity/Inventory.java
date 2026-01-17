package com.nerya.neryaallnaturals.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand = 0;

    @Column(name = "quantity_reserved")
    private Integer quantityReserved = 0; // Reserved in cart or pending orders

    @Column(name = "quantity_sold")
    private Integer quantitySold = 0;

    @Column(name = "min_stock_level")
    private Integer minStockLevel = 5; // Alert when stock is below this

    @Column(name = "max_stock_level")
    private Integer maxStockLevel = 1000;

    @Column(name = "reorder_quantity")
    private Integer reorderQuantity = 50; // Quantity to order when reordering

    @Column(name = "last_updated_by")
    private String lastUpdatedBy; // User or admin who last updated

    public Integer getAvailableQuantity() {
        return quantityOnHand - quantityReserved;
    }

    public Boolean isLowStock() {
        return quantityOnHand <= minStockLevel;
    }
}

