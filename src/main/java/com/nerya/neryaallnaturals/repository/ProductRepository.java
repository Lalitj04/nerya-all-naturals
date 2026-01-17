package com.nerya.neryaallnaturals.repository;

import com.nerya.neryaallnaturals.entity.Category;
import com.nerya.neryaallnaturals.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Optional<Product> findBySku(String sku);
    
    List<Product> findByIsActiveTrue();
    
    List<Product> findByCategoryAndIsActiveTrue(Category category);
    
    List<Product> findByCategory(Category category);
    
    List<Product> findByIsFeaturedTrueAndIsActiveTrue();
    
    List<Product> findByInStockTrueAndIsActiveTrue();
    
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.isActive = true")
    List<Product> findByCategoryIdAndActive(@Param("categoryId") Long categoryId);
    
    @Query("SELECT p FROM Product p WHERE p.name LIKE CONCAT('%', :keyword, '%') OR p.shortDescription LIKE CONCAT('%', :keyword, '%') OR p.longDescription LIKE CONCAT('%', :keyword, '%')")
    List<Product> searchProducts(@Param("keyword") String keyword);
}

