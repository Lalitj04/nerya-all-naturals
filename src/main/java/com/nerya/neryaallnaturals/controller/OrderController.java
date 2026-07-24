package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.annotation.CustomerOnly;
import com.nerya.neryaallnaturals.dto.CheckoutRequest;
import com.nerya.neryaallnaturals.dto.OrderResponse;
import com.nerya.neryaallnaturals.dto.PagedResponse;
import com.nerya.neryaallnaturals.dto.UpdateOrderStatusRequest;
import com.nerya.neryaallnaturals.entity.OrderStatus;
import com.nerya.neryaallnaturals.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Order APIs (T47/T48). Customer endpoints are principal-scoped — an order is resolved from the
 * JWT plus its number, never a raw id — so one customer can never read or cancel another's order.
 * Admin endpoints live under {@code /admin} and require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    // ---- customer (T47) ----

    @PostMapping("/checkout")
    @CustomerOnly
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        log.info("Customer '{}' checking out to address {}", authentication.getName(), request.getAddressId());
        OrderResponse order = orderService.checkout(
                authentication.getName(), request.getAddressId(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/me")
    @CustomerOnly
    public ResponseEntity<PagedResponse<OrderResponse>> getMyOrders(Authentication authentication,
                                                                    Pageable pageable) {
        return ResponseEntity.ok(orderService.getMyOrders(authentication.getName(), pageable));
    }

    @GetMapping("/me/{orderNumber}")
    @CustomerOnly
    public ResponseEntity<OrderResponse> getMyOrder(@PathVariable String orderNumber,
                                                    Authentication authentication) {
        return ResponseEntity.ok(orderService.getMyOrder(authentication.getName(), orderNumber));
    }

    @PostMapping("/me/{orderNumber}/cancel")
    @CustomerOnly
    public ResponseEntity<OrderResponse> cancelMyOrder(@PathVariable String orderNumber,
                                                       Authentication authentication) {
        log.info("Customer '{}' cancelling order {}", authentication.getName(), orderNumber);
        return ResponseEntity.ok(orderService.cancelMyOrder(authentication.getName(), orderNumber));
    }

    // ---- admin (T48) ----

    @GetMapping("/admin")
    @AdminOnly
    public ResponseEntity<PagedResponse<OrderResponse>> getOrdersForAdmin(
            @RequestParam(value = "status", required = false) OrderStatus status,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        return ResponseEntity.ok(
                orderService.getOrdersForAdmin(status, customerId, from, to, pageable));
    }

    @GetMapping("/admin/{orderNumber}")
    @AdminOnly
    public ResponseEntity<OrderResponse> getOrderForAdmin(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderForAdmin(orderNumber));
    }

    @PutMapping("/admin/{orderNumber}/status")
    @AdminOnly
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable String orderNumber,
                                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        log.info("Admin advancing order {} to {}", orderNumber, request.getStatus());
        return ResponseEntity.ok(orderService.updateStatus(orderNumber, request.getStatus()));
    }
}
