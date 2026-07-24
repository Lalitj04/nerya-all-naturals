package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.CustomerOnly;
import com.nerya.neryaallnaturals.dto.AddToCartRequest;
import com.nerya.neryaallnaturals.dto.CartResponse;
import com.nerya.neryaallnaturals.dto.UpdateCartItemRequest;
import com.nerya.neryaallnaturals.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Cart APIs (T42). Every endpoint is customer-scoped: the cart is resolved from the JWT
 * principal, never from a path id, so a customer can only touch their own cart.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
public class CartController {

    private final CartService cartService;

    /**
     * Full cart with per-line totals and grand total.
     */
    @GetMapping
    @CustomerOnly
    public ResponseEntity<CartResponse> getCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.getCart(authentication.getName()));
    }

    /**
     * Add a product to the cart, merging into an existing line if present.
     */
    @PostMapping("/items")
    @CustomerOnly
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddToCartRequest request,
                                                 Authentication authentication) {
        log.info("Customer '{}' adding product {} x{} to cart",
                authentication.getName(), request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(cartService.addItem(
                authentication.getName(), request.getProductId(), request.getQuantity()));
    }

    /**
     * Set the exact quantity of a line (0 removes it).
     */
    @PutMapping("/items/{productId}")
    @CustomerOnly
    public ResponseEntity<CartResponse> updateItem(@PathVariable Long productId,
                                                    @Valid @RequestBody UpdateCartItemRequest request,
                                                    Authentication authentication) {
        log.info("Customer '{}' setting product {} quantity to {}",
                authentication.getName(), productId, request.getQuantity());
        return ResponseEntity.ok(cartService.updateItemQuantity(
                authentication.getName(), productId, request.getQuantity()));
    }

    /**
     * Remove a single line from the cart.
     */
    @DeleteMapping("/items/{productId}")
    @CustomerOnly
    public ResponseEntity<CartResponse> removeItem(@PathVariable Long productId,
                                                   Authentication authentication) {
        log.info("Customer '{}' removing product {} from cart", authentication.getName(), productId);
        return ResponseEntity.ok(cartService.removeItem(authentication.getName(), productId));
    }

    /**
     * Empty the cart.
     */
    @DeleteMapping
    @CustomerOnly
    public ResponseEntity<CartResponse> clear(Authentication authentication) {
        log.info("Customer '{}' clearing cart", authentication.getName());
        return ResponseEntity.ok(cartService.clear(authentication.getName()));
    }
}
