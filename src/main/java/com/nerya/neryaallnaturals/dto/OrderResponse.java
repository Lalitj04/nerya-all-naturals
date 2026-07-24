package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.Order;
import com.nerya.neryaallnaturals.entity.OrderStatus;
import com.nerya.neryaallnaturals.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal discount;
    private BigDecimal total;
    private List<OrderItemResponse> items;
    private ShippingAddress shippingAddress;
    private LocalDateTime placedAt;
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShippingAddress {
        private String name;
        private String phone;
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String pincode;
    }

    public static OrderResponse fromEntity(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::fromEntity)
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .discount(order.getDiscount())
                .total(order.getTotal())
                .items(items)
                .shippingAddress(ShippingAddress.builder()
                        .name(order.getShipName())
                        .phone(order.getShipPhone())
                        .line1(order.getShipLine1())
                        .line2(order.getShipLine2())
                        .city(order.getShipCity())
                        .state(order.getShipState())
                        .pincode(order.getShipPincode())
                        .build())
                .placedAt(order.getPlacedAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
