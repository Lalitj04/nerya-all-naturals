package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.CustomerOnly;
import com.nerya.neryaallnaturals.dto.AddressRequest;
import com.nerya.neryaallnaturals.dto.AddressResponse;
import com.nerya.neryaallnaturals.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/me/addresses")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @CustomerOnly
    public ResponseEntity<List<AddressResponse>> getMyAddresses(Authentication authentication) {
        return ResponseEntity.ok(addressService.getMyAddresses(authentication.getName()));
    }

    @PostMapping
    @CustomerOnly
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody AddressRequest request,
                                                           Authentication authentication) {
        log.info("Customer '{}' adding a new address", authentication.getName());
        AddressResponse response = addressService.createAddress(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{addressId}")
    @CustomerOnly
    public ResponseEntity<AddressResponse> updateAddress(@PathVariable Long addressId,
                                                           @Valid @RequestBody AddressRequest request,
                                                           Authentication authentication) {
        log.info("Customer '{}' updating address {}", authentication.getName(), addressId);
        return ResponseEntity.ok(addressService.updateAddress(authentication.getName(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    @CustomerOnly
    public ResponseEntity<Void> deleteAddress(@PathVariable Long addressId, Authentication authentication) {
        log.info("Customer '{}' deleting address {}", authentication.getName(), addressId);
        addressService.deleteAddress(authentication.getName(), addressId);
        return ResponseEntity.noContent().build();
    }
}
