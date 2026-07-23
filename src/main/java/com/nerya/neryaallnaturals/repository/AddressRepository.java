package com.nerya.neryaallnaturals.repository;

import com.nerya.neryaallnaturals.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(Long customerId);

    Optional<Address> findByIdAndCustomerId(Long id, Long customerId);
}
