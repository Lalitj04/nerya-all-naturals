package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.PaymentProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotBlank(message = "Order number is required")
    private String orderNumber;

    @NotNull(message = "Provider is required")
    private PaymentProvider provider;
}
