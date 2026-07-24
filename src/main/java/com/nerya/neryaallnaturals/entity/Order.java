package com.nerya.neryaallnaturals.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed order (T45/T46). Everything a customer bought is snapshotted here at checkout time —
 * line prices on {@link OrderItem} and the shipping address on this row — so later catalog or
 * address edits never rewrite order history.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** Client-supplied key that makes a replayed checkout return this same order (T50). */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    // ---- shipping address snapshot ----
    @Column(name = "ship_name", nullable = false, length = 100)
    private String shipName;

    @Column(name = "ship_phone", length = 20)
    private String shipPhone;

    @Column(name = "ship_line1", nullable = false, length = 255)
    private String shipLine1;

    @Column(name = "ship_line2", length = 255)
    private String shipLine2;

    @Column(name = "ship_city", nullable = false, length = 100)
    private String shipCity;

    @Column(name = "ship_state", nullable = false, length = 100)
    private String shipState;

    @Column(name = "ship_pincode", nullable = false, length = 20)
    private String shipPincode;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /** Add a line and keep the bidirectional link consistent. */
    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
