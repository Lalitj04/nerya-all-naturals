package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.CartItemResponse;
import com.nerya.neryaallnaturals.dto.CartResponse;
import com.nerya.neryaallnaturals.entity.Cart;
import com.nerya.neryaallnaturals.entity.CartItem;
import com.nerya.neryaallnaturals.entity.Customer;
import com.nerya.neryaallnaturals.entity.Inventory;
import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.exception.InsufficientStockException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.CartItemRepository;
import com.nerya.neryaallnaturals.repository.CartRepository;
import com.nerya.neryaallnaturals.repository.InventoryRepository;
import com.nerya.neryaallnaturals.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cart domain logic (T41): a single persistent cart per customer, with add/merge, quantity
 * update, remove, clear and total computation. Line and grand totals are always derived from
 * each product's CURRENT selling price — nothing is snapshotted until an order is placed (P5).
 * Stock-limit enforcement (T43) rejects any operation that would push a line beyond the
 * product's available quantity (quantityOnHand - quantityReserved).
 *
 * <p>Every operation is scoped to the authenticated customer's username, so a customer can
 * only ever read or mutate their own cart.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerService customerService;

    /**
     * Fetch the customer's cart as a response, creating an empty one on first access.
     */
    @Transactional
    public CartResponse getCart(String username) {
        return toResponse(getOrCreateCart(username));
    }

    /**
     * Add a product to the cart, or merge into the existing line by increasing its quantity.
     */
    @Transactional
    public CartResponse addItem(String username, Long productId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        Cart cart = getOrCreateCart(username);
        Product product = getProduct(productId);

        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);
        int newQuantity = existing.map(item -> item.getQuantity() + quantity).orElse(quantity);
        assertStockAvailable(product, newQuantity);

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
            log.info("Merged {} into cart {} for product {} (new qty {})",
                    quantity, cart.getId(), productId, newQuantity);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .build();
            cart.getItems().add(item);
            cartItemRepository.save(item);
            log.info("Added product {} x{} to cart {}", productId, quantity, cart.getId());
        }
        return toResponse(getOrCreateCart(username));
    }

    /**
     * Set the exact quantity of a product line. A quantity of 0 removes the line.
     */
    @Transactional
    public CartResponse updateItemQuantity(String username, Long productId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (quantity == 0) {
            return removeItem(username, productId);
        }
        Cart cart = getOrCreateCart(username);
        Product product = getProduct(productId);
        assertStockAvailable(product, quantity);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product " + productId + " is not in the cart"));
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        log.info("Set product {} quantity to {} in cart {}", productId, quantity, cart.getId());
        return toResponse(getOrCreateCart(username));
    }

    /**
     * Remove a single product line from the cart.
     */
    @Transactional
    public CartResponse removeItem(String username, Long productId) {
        Cart cart = getOrCreateCart(username);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product " + productId + " is not in the cart"));
        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        log.info("Removed product {} from cart {}", productId, cart.getId());
        return toResponse(getOrCreateCart(username));
    }

    /**
     * Empty the cart, keeping the cart itself.
     */
    @Transactional
    public CartResponse clear(String username) {
        Cart cart = getOrCreateCart(username);
        cart.getItems().clear();
        cartRepository.save(cart);
        log.info("Cleared cart {}", cart.getId());
        return toResponse(cart);
    }

    /**
     * Fetch the customer's cart, creating an empty one on first access.
     */
    private Cart getOrCreateCart(String username) {
        Customer customer = customerService.getCustomerByUsername(username);
        return cartRepository.findByCustomerId(customer.getId())
                .orElseGet(() -> {
                    Cart cart = Cart.builder().customer(customer).build();
                    log.info("Creating cart for customer ID: {}", customer.getId());
                    return cartRepository.save(cart);
                });
    }

    /**
     * Reject a requested quantity that exceeds the product's available stock. Products with no
     * inventory row are treated as having zero available stock.
     */
    private void assertStockAvailable(Product product, int requestedQuantity) {
        int available = inventoryRepository.findByProductId(product.getId())
                .map(Inventory::getAvailableQuantity)
                .orElse(0);
        if (requestedQuantity > available) {
            throw new InsufficientStockException(
                    "Only " + available + " unit(s) of '" + product.getName() + "' are available");
        }
    }

    /**
     * Line total for a single item: current selling price × quantity.
     */
    private BigDecimal lineTotal(CartItem item) {
        return item.getProduct().getSellingPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> {
                    Product product = item.getProduct();
                    return CartItemResponse.builder()
                            .productId(product.getId())
                            .sku(product.getSku())
                            .name(product.getName())
                            .unitPrice(product.getSellingPrice())
                            .quantity(item.getQuantity())
                            .lineTotal(lineTotal(item))
                            .build();
                })
                .collect(Collectors.toList());

        BigDecimal grandTotal = items.stream()
                .map(CartItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalItems = items.stream().mapToInt(CartItemResponse::getQuantity).sum();

        return CartResponse.builder()
                .id(cart.getId())
                .items(items)
                .totalItems(totalItems)
                .grandTotal(grandTotal)
                .build();
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with ID: " + productId));
    }
}
