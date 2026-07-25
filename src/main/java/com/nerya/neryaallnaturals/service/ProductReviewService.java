package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.PagedResponse;
import com.nerya.neryaallnaturals.dto.ReviewRequest;
import com.nerya.neryaallnaturals.dto.ReviewResponse;
import com.nerya.neryaallnaturals.entity.Customer;
import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.entity.ProductReview;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.OrderRepository;
import com.nerya.neryaallnaturals.repository.ProductRepository;
import com.nerya.neryaallnaturals.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Product reviews (P7). One review per customer per product, enforced by the DB unique
 * constraint (T56); a customer is only marked as a verified purchaser if they have a delivered
 * order containing the product (T57).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerService customerService;

    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> getReviewsForProduct(Long productId, Pageable pageable) {
        Page<ProductReview> page = reviewRepository.findByProductId(productId, normalize(pageable));
        List<ReviewResponse> content = page.getContent().stream()
                .map(ReviewResponse::fromEntity)
                .collect(Collectors.toList());
        return PagedResponse.of(content, page);
    }

    @Transactional
    public ReviewResponse createMyReview(String username, Long productId, ReviewRequest request) {
        Customer customer = customerService.getCustomerByUsername(username);
        Product product = getProduct(productId);

        if (reviewRepository.findByProductIdAndCustomerId(productId, customer.getId()).isPresent()) {
            throw new ConflictException("You have already reviewed this product");
        }

        boolean verifiedPurchase = orderRepository.existsDeliveredOrderWithProduct(customer.getId(), productId);

        ProductReview review = ProductReview.builder()
                .product(product)
                .customer(customer)
                .rating(request.getRating())
                .title(request.getTitle())
                .comment(request.getComment())
                .isVerifiedPurchase(verifiedPurchase)
                .build();

        ProductReview saved = reviewRepository.save(review);
        recomputeProductRating(productId);
        log.info("Customer {} reviewed product {} ({} stars)", customer.getId(), productId, request.getRating());
        return ReviewResponse.fromEntity(saved);
    }

    @Transactional
    public ReviewResponse updateMyReview(String username, Long productId, ReviewRequest request) {
        Customer customer = customerService.getCustomerByUsername(username);
        ProductReview review = reviewRepository.findByProductIdAndCustomerId(productId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product"));

        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        ProductReview saved = reviewRepository.save(review);
        recomputeProductRating(productId);
        return ReviewResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteMyReview(String username, Long productId) {
        Customer customer = customerService.getCustomerByUsername(username);
        ProductReview review = reviewRepository.findByProductIdAndCustomerId(productId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product"));
        reviewRepository.delete(review);
        recomputeProductRating(productId);
    }

    // ---- admin moderation ----

    @Transactional
    public void deleteReviewAsAdmin(Long reviewId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        Long productId = review.getProduct().getId();
        reviewRepository.delete(review);
        recomputeProductRating(productId);
        log.info("Admin deleted review {}", reviewId);
    }

    // ---- helpers ----

    private void recomputeProductRating(Long productId) {
        Product product = getProduct(productId);
        long count = reviewRepository.countByProductId(productId);
        Double average = reviewRepository.findAverageRatingByProductId(productId);
        product.setTotalReviews((int) count);
        product.setAverageRating(count == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        productRepository.save(product);
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Pageable normalize(Pageable pageable) {
        int size = Math.min(pageable.isPaged() ? pageable.getPageSize() : 20, 100);
        int number = pageable.isPaged() ? pageable.getPageNumber() : 0;
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(number, size, sort);
    }
}
