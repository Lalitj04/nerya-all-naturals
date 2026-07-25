package com.nerya.neryaallnaturals.repository;

import com.nerya.neryaallnaturals.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    Optional<Payment> findTopByOrderIdOrderByIdDesc(Long orderId);
}
