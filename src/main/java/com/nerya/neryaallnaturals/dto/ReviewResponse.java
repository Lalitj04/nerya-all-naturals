package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.ProductReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private Long id;
    private Long productId;
    private String customerName;
    private Integer rating;
    private String title;
    private String comment;
    private Boolean verifiedPurchase;
    private LocalDateTime createdAt;

    public static ReviewResponse fromEntity(ProductReview review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .customerName(review.getCustomer().getCustomerName())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .verifiedPurchase(review.getIsVerifiedPurchase())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
