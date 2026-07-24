package com.nerya.neryaallnaturals.entity;

import java.util.Set;

/**
 * Fulfilment lifecycle of an order (T45). Transitions are one-directional along the happy path,
 * with cancellation allowed only before the goods ship. {@link #canTransitionTo} encodes the
 * legal state machine enforced on every status change (T48).
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Set<OrderStatus> CANCELLABLE = Set.of(PENDING, CONFIRMED, PACKED);

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == PACKED || target == CANCELLED;
            case PACKED -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false; // terminal
        };
    }

    /** Whether a customer/admin may still cancel an order in this state (reservation released). */
    public boolean isCancellable() {
        return CANCELLABLE.contains(this);
    }
}
