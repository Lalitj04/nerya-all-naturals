package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.Payment;
import com.nerya.neryaallnaturals.entity.PaymentProvider;
import com.nerya.neryaallnaturals.entity.PaymentTxnStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payment attempt result. For a gateway provider, {@code checkoutKeyId}/{@code providerOrderId}
 * are what the UI hands to the provider's checkout widget; both are null for COD.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private String orderNumber;
    private PaymentProvider provider;
    private PaymentTxnStatus status;
    private BigDecimal amount;
    private String currency;
    private String providerOrderId;
    private String checkoutKeyId;

    public static PaymentResponse fromEntity(Payment payment) {
        return fromEntity(payment, null);
    }

    public static PaymentResponse fromEntity(Payment payment, String checkoutKeyId) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderNumber(payment.getOrder().getOrderNumber())
                .provider(payment.getProvider())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .providerOrderId(payment.getProviderOrderId())
                .checkoutKeyId(checkoutKeyId)
                .build();
    }
}
