package com.nerya.neryaallnaturals.repository;

import com.nerya.neryaallnaturals.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    
    List<ProductReview> findByProductId(Long productId);
    
    List<ProductReview> findByCustomerId(Long customerId);
}

