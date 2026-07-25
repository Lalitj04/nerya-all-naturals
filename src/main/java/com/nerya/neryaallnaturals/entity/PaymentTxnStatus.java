package com.nerya.neryaallnaturals.entity;

/**
 * Lifecycle of a single {@link Payment} attempt (T53). Distinct from {@link PaymentStatus}, which
 * tracks the order's overall paid/unpaid state.
 */
public enum PaymentTxnStatus {
    CREATED,
    SUCCESS,
    FAILED,
    REFUNDED
}
