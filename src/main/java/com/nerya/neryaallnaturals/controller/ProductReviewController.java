package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.annotation.CustomerOnly;
import com.nerya.neryaallnaturals.dto.PagedResponse;
import com.nerya.neryaallnaturals.dto.ReviewRequest;
import com.nerya.neryaallnaturals.dto.ReviewResponse;
import com.nerya.neryaallnaturals.service.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Product review APIs (T56/T57). Reads are public; writes are principal-scoped, one per customer per product. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProductReviewController {

    private final ProductReviewService reviewService;

    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<PagedResponse<ReviewResponse>> getReviews(@PathVariable Long productId,
                                                                     Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviewsForProduct(productId, pageable));
    }

    @PostMapping("/api/products/{productId}/reviews/me")
    @CustomerOnly
    public ResponseEntity<ReviewResponse> createMyReview(@PathVariable Long productId,
                                                          @Valid @RequestBody ReviewRequest request,
                                                          Authentication authentication) {
        log.info("Customer '{}' reviewing product {}", authentication.getName(), productId);
        ReviewResponse response = reviewService.createMyReview(authentication.getName(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/products/{productId}/reviews/me")
    @CustomerOnly
    public ResponseEntity<ReviewResponse> updateMyReview(@PathVariable Long productId,
                                                          @Valid @RequestBody ReviewRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(reviewService.updateMyReview(authentication.getName(), productId, request));
    }

    @DeleteMapping("/api/products/{productId}/reviews/me")
    @CustomerOnly
    public ResponseEntity<Void> deleteMyReview(@PathVariable Long productId, Authentication authentication) {
        reviewService.deleteMyReview(authentication.getName(), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/reviews/admin/{reviewId}")
    @AdminOnly
    public ResponseEntity<Void> deleteReviewAsAdmin(@PathVariable Long reviewId) {
        log.info("Admin moderating away review {}", reviewId);
        reviewService.deleteReviewAsAdmin(reviewId);
        return ResponseEntity.noContent().build();
    }
}
