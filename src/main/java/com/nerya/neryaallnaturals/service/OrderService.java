package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.OrderResponse;
import com.nerya.neryaallnaturals.dto.PagedResponse;
import com.nerya.neryaallnaturals.entity.*;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.AddressRepository;
import com.nerya.neryaallnaturals.repository.CartRepository;
import com.nerya.neryaallnaturals.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orders & checkout (P5). The checkout method is the critical section: it reserves stock under a
 * row lock, snapshots prices and the shipping address, and clears the cart — all in one
 * transaction so stock can never over-sell and orders never capture a half-built state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final InventoryService inventoryService;
    private final CustomerService customerService;

    /**
     * Turn the customer's cart into a PENDING order (T46).
     *
     * <p>Steps, all in one transaction: (1) if an idempotency key was supplied and already used
     * by this customer, return that order unchanged (T50); (2) load the cart, rejecting an empty
     * one; (3) for each line, lock inventory and reserve stock (over-reservation is impossible);
     * (4) snapshot the shipping address and current prices onto the order; (5) clear the cart.
     */
    @Transactional
    public OrderResponse checkout(String username, Long addressId, String idempotencyKey) {
        Customer customer = customerService.getCustomerByUsername(username);

        if (StringUtils.hasText(idempotencyKey)) {
            Optional<Order> replay = orderRepository
                    .findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey);
            if (replay.isPresent()) {
                log.info("Idempotent checkout replay for customer {} key {} -> order {}",
                        customer.getId(), idempotencyKey, replay.get().getOrderNumber());
                return OrderResponse.fromEntity(replay.get());
            }
        }

        Cart cart = cartRepository.findByCustomerId(customer.getId())
                .orElseThrow(() -> new ConflictException("Your cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new ConflictException("Your cart is empty");
        }

        Address address = addressRepository.findByIdAndCustomerId(addressId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .idempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null)
                .shippingFee(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .placedAt(LocalDateTime.now())
                .build();
        applyShippingSnapshot(order, address, customer);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            int quantity = cartItem.getQuantity();

            // Reserve under a row lock — throws InsufficientStockException if short.
            inventoryService.reserve(product.getId(), quantity);

            BigDecimal unitPrice = product.getSellingPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .sku(product.getSku())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .lineTotal(lineTotal)
                    .build();
            order.addItem(item);
        }

        order.setSubtotal(subtotal);
        order.setTotal(subtotal.add(order.getShippingFee()).subtract(order.getDiscount()));

        Order saved = persistWithOrderNumber(order);

        // Cart is consumed by the order.
        cart.getItems().clear();
        cartRepository.save(cart);

        log.info("Placed order {} for customer {} ({} line(s), total {})",
                saved.getOrderNumber(), customer.getId(), saved.getItems().size(), saved.getTotal());
        return OrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getMyOrders(String username, Pageable pageable) {
        Customer customer = customerService.getCustomerByUsername(username);
        Page<Order> page = orderRepository.findByCustomerId(customer.getId(), normalize(pageable));
        List<OrderResponse> content = page.getContent().stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        return PagedResponse.of(content, page);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(String username, String orderNumber) {
        return OrderResponse.fromEntity(findOwnedOrder(username, orderNumber));
    }

    /**
     * Customer-initiated cancellation (T47): allowed only while PENDING/CONFIRMED/PACKED, and it
     * releases every line's reservation back to available stock (T49).
     */
    @Transactional
    public OrderResponse cancelMyOrder(String username, String orderNumber) {
        Order order = findOwnedOrder(username, orderNumber);
        return OrderResponse.fromEntity(cancel(order));
    }

    // ---- payments (T54) ----

    /** Look up an order the given customer owns, for payment creation. */
    @Transactional(readOnly = true)
    public Order getOwnedOrderEntity(String username, String orderNumber) {
        return findOwnedOrder(username, orderNumber);
    }

    @Transactional(readOnly = true)
    public Order getOrderEntity(String orderNumber) {
        return findOrder(orderNumber);
    }

    /** Flip an order to paid/confirmed once its payment succeeds (COD or gateway webhook). */
    @Transactional
    public void markOrderPaid(Order order) {
        order.setPaymentStatus(PaymentStatus.PAID);
        if (order.getStatus().canTransitionTo(OrderStatus.CONFIRMED)) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        orderRepository.save(order);
        log.info("Order {} marked PAID/CONFIRMED", order.getOrderNumber());
    }

    /** Record COD as the chosen payment method without marking the order paid yet. */
    @Transactional
    public void markOrderCod(Order order) {
        order.setPaymentStatus(PaymentStatus.COD);
        if (order.getStatus().canTransitionTo(OrderStatus.CONFIRMED)) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        orderRepository.save(order);
        log.info("Order {} marked COD/CONFIRMED", order.getOrderNumber());
    }

    // ---- admin (T48) ----

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersForAdmin(OrderStatus status, Long customerId,
                                                          LocalDateTime from, LocalDateTime to,
                                                          Pageable pageable) {
        Page<Order> page = orderRepository.findForAdmin(status, customerId, from, to, normalize(pageable));
        List<OrderResponse> content = page.getContent().stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        return PagedResponse.of(content, page);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderForAdmin(String orderNumber) {
        return OrderResponse.fromEntity(findOrder(orderNumber));
    }

    /**
     * Admin status advance (T48/T49) with legal-transition validation. SHIPPED converts each
     * line's reservation into a sale; CANCELLED releases each reservation.
     */
    @Transactional
    public OrderResponse updateStatus(String orderNumber, OrderStatus target) {
        Order order = findOrder(orderNumber);
        OrderStatus current = order.getStatus();

        if (current == target) {
            return OrderResponse.fromEntity(order);
        }
        if (target == OrderStatus.CANCELLED) {
            return OrderResponse.fromEntity(cancel(order));
        }
        if (!current.canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot change order status from " + current + " to " + target);
        }

        if (target == OrderStatus.SHIPPED) {
            order.getItems().forEach(item ->
                    inventoryService.ship(item.getProduct().getId(), item.getQuantity()));
        }
        order.setStatus(target);
        Order saved = orderRepository.save(order);
        log.info("Order {} status {} -> {}", orderNumber, current, target);
        return OrderResponse.fromEntity(saved);
    }

    // ---- helpers ----

    /**
     * Cancel an order, releasing reservations. Shared by the customer and admin paths so the
     * cancellation rules live in exactly one place.
     */
    private Order cancel(Order order) {
        if (!order.getStatus().isCancellable()) {
            throw new ConflictException(
                    "Order in status " + order.getStatus() + " can no longer be cancelled");
        }
        order.getItems().forEach(item ->
                inventoryService.release(item.getProduct().getId(), item.getQuantity()));
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }
        log.info("Cancelled order {}", order.getOrderNumber());
        return orderRepository.save(order);
    }

    /** Clamp page size (max 100, default 20) and default to newest-first when unsorted. */
    private Pageable normalize(Pageable pageable) {
        int size = Math.min(pageable.isPaged() ? pageable.getPageSize() : 20, 100);
        int number = pageable.isPaged() ? pageable.getPageNumber() : 0;
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "placedAt");
        return PageRequest.of(number, size, sort);
    }

    private Order findOwnedOrder(String username, String orderNumber) {
        Customer customer = customerService.getCustomerByUsername(username);
        return orderRepository.findByCustomerIdAndOrderNumber(customer.getId(), orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private Order findOrder(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private void applyShippingSnapshot(Order order, Address address, Customer customer) {
        order.setShipName(address.getName());
        order.setShipPhone(customer.getCustomerPhone());
        order.setShipLine1(address.getAddressLine1());
        order.setShipLine2(address.getAddressLine2());
        order.setShipCity(address.getCity());
        order.setShipState(address.getState());
        order.setShipPincode(address.getPinCode());
    }

    /**
     * Assign a human-readable {@code NRY-<year>-<6 digits>} number derived from the DB-assigned
     * id, guaranteeing uniqueness without a race. We persist once to obtain the id, then set and
     * re-save the number. A duplicate idempotency key surfaces here as a constraint violation and
     * is translated to a 409.
     */
    private Order persistWithOrderNumber(Order order) {
        // A unique, non-null placeholder so the first insert satisfies the NOT NULL/UNIQUE column
        // before the id (and thus the final number) exists; overwritten immediately below.
        order.setOrderNumber("TMP-" + UUID.randomUUID());
        try {
            Order saved = orderRepository.saveAndFlush(order);
            saved.setOrderNumber(String.format("NRY-%d-%06d", Year.now().getValue(), saved.getId()));
            return orderRepository.saveAndFlush(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("This checkout has already been submitted");
        }
    }
}
