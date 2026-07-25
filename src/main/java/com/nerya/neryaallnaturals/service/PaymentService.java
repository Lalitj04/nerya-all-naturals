package com.nerya.neryaallnaturals.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nerya.neryaallnaturals.config.RazorpayProperties;
import com.nerya.neryaallnaturals.dto.PaymentResponse;
import com.nerya.neryaallnaturals.entity.*;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ForbiddenException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments (P6). COD is recorded synchronously; the Razorpay path creates a provider order and
 * waits for the signature-verified webhook to flip it to SUCCESS (T54).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final RazorpayGatewayService gatewayService;
    private final RazorpayProperties razorpayProperties;

    @Transactional
    public PaymentResponse createPayment(String username, String orderNumber, PaymentProvider provider) {
        Order order = orderService.getOwnedOrderEntity(username, orderNumber);
        if (order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new ConflictException("This order is already " + order.getPaymentStatus());
        }

        if (provider == PaymentProvider.COD) {
            Payment payment = Payment.builder()
                    .order(order)
                    .provider(PaymentProvider.COD)
                    .amount(order.getTotal())
                    .status(PaymentTxnStatus.SUCCESS)
                    .build();
            Payment saved = paymentRepository.save(payment);
            orderService.markOrderCod(order);
            log.info("Recorded COD payment for order {}", orderNumber);
            return PaymentResponse.fromEntity(saved);
        }

        String providerOrderId = gatewayService.createOrder(order.getTotal(), "INR", order.getOrderNumber());
        Payment payment = Payment.builder()
                .order(order)
                .provider(provider)
                .providerOrderId(providerOrderId)
                .amount(order.getTotal())
                .status(PaymentTxnStatus.CREATED)
                .build();
        Payment saved = paymentRepository.save(payment);
        log.info("Created {} payment order {} for order {}", provider, providerOrderId, orderNumber);
        return PaymentResponse.fromEntity(saved, razorpayProperties.getKeyId());
    }

    /**
     * Handle a Razorpay webhook call (T54). The signature must be verified against the exact raw
     * body before any JSON parsing happens. A replay of an already-terminal payment is a no-op
     * (T55) so a duplicated webhook delivery never double-applies its side effects.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        if (!gatewayService.verifyWebhookSignature(rawBody, signatureHeader)) {
            throw new ForbiddenException("Invalid webhook signature");
        }

        JsonNode root = gatewayService.parse(rawBody);
        String event = root.path("event").asText("");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String providerOrderId = paymentEntity.path("order_id").asText(null);
        String providerPaymentId = paymentEntity.path("id").asText(null);

        if (providerOrderId == null) {
            log.warn("Ignoring webhook event {} with no order_id", event);
            return;
        }

        Payment payment = paymentRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown payment order " + providerOrderId));

        if (payment.getStatus() == PaymentTxnStatus.SUCCESS || payment.getStatus() == PaymentTxnStatus.FAILED) {
            log.info("Ignoring duplicate webhook for already-{} payment {}", payment.getStatus(), providerOrderId);
            return;
        }

        payment.setProviderPaymentId(providerPaymentId);

        if ("payment.captured".equals(event)) {
            payment.setStatus(PaymentTxnStatus.SUCCESS);
            paymentRepository.save(payment);
            orderService.markOrderPaid(payment.getOrder());
            log.info("Payment {} captured for order {}", providerPaymentId, payment.getOrder().getOrderNumber());
        } else if ("payment.failed".equals(event)) {
            payment.setStatus(PaymentTxnStatus.FAILED);
            paymentRepository.save(payment);
            log.info("Payment {} failed for order {}", providerPaymentId, payment.getOrder().getOrderNumber());
        } else {
            log.info("Ignoring unhandled webhook event {}", event);
        }
    }
}
