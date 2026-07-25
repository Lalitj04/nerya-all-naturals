package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.CustomerOnly;
import com.nerya.neryaallnaturals.dto.CreatePaymentRequest;
import com.nerya.neryaallnaturals.dto.PaymentResponse;
import com.nerya.neryaallnaturals.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Payment APIs (T54). {@code /create} is principal-scoped like the order endpoints; {@code
 * /webhook} is the one deliberately public path in this controller and is instead authenticated
 * by verifying the provider's signature over the raw request body.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    @CustomerOnly
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request,
                                                          Authentication authentication) {
        log.info("Customer '{}' starting {} payment for order {}",
                authentication.getName(), request.getProvider(), request.getOrderNumber());
        PaymentResponse response = paymentService.createPayment(
                authentication.getName(), request.getOrderNumber(), request.getProvider());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(HttpServletRequest request,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature)
            throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
