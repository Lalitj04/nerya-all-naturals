package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.CustomerOnly;
import com.nerya.neryaallnaturals.dto.CustomerProfileUpdateRequest;
import com.nerya.neryaallnaturals.dto.CustomerResponse;
import com.nerya.neryaallnaturals.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Get the authenticated customer's own profile. Resolved from the JWT principal,
     * never from a path id, so a customer can never read another customer's profile.
     */
    @GetMapping("/me")
    @CustomerOnly
    public ResponseEntity<CustomerResponse> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(customerService.getMyProfile(authentication.getName()));
    }

    /**
     * Update the authenticated customer's own profile.
     */
    @PutMapping("/me")
    @CustomerOnly
    public ResponseEntity<CustomerResponse> updateMyProfile(
            @Valid @RequestBody CustomerProfileUpdateRequest request,
            Authentication authentication) {
        log.info("Updating profile for customer '{}'", authentication.getName());
        return ResponseEntity.ok(customerService.updateMyProfile(authentication.getName(), request));
    }
}
