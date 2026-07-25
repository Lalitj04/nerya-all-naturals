package com.nerya.neryaallnaturals.repository;

import com.nerya.neryaallnaturals.entity.Order;
import com.nerya.neryaallnaturals.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByCustomerIdAndOrderNumber(Long customerId, String orderNumber);

    /** Idempotency lookup (T50): the same key from the same customer must map to one order. */
    Optional<Order> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    /**
     * Admin listing with all filters optional (T48): a null argument disables that predicate.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:customerId IS NULL OR o.customer.id = :customerId)
              AND (:from IS NULL OR o.placedAt >= :from)
              AND (:to IS NULL OR o.placedAt <= :to)
            """)
    Page<Order> findForAdmin(@Param("status") OrderStatus status,
                             @Param("customerId") Long customerId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             Pageable pageable);

    /** Verified-purchase rule (T57): has this customer ever received a delivered order line for the product? */
    @Query("""
            SELECT COUNT(o) > 0 FROM Order o JOIN o.items i
            WHERE o.customer.id = :customerId AND i.product.id = :productId AND o.status = 'DELIVERED'
            """)
    boolean existsDeliveredOrderWithProduct(@Param("customerId") Long customerId, @Param("productId") Long productId);
}
