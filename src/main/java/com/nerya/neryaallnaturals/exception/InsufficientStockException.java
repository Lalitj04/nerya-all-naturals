package com.nerya.neryaallnaturals.exception;

/**
 * Raised when a cart operation would push a line's quantity beyond the product's
 * available stock (quantityOnHand - quantityReserved). Surfaces as HTTP 409 with the
 * {@code INSUFFICIENT_STOCK} error code (T43).
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
