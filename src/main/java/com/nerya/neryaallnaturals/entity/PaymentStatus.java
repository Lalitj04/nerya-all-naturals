package com.nerya.neryaallnaturals.entity;

/**
 * Payment state of an order (T45). Kept independent of {@link OrderStatus} so, e.g., a COD order
 * can be CONFIRMED while still UNPAID. Fully wired to a gateway in Phase P6.
 */
public enum PaymentStatus {
    UNPAID,
    PAID,
    REFUNDED,
    COD
}
