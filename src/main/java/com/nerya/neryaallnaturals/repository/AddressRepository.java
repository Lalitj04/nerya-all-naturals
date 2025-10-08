package com.nerya.neryaallnaturals.repository;

import com.nerya.neryaallnaturals.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
}