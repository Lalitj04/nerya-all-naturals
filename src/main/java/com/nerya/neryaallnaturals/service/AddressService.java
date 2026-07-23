package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.AddressRequest;
import com.nerya.neryaallnaturals.dto.AddressResponse;
import com.nerya.neryaallnaturals.entity.Address;
import com.nerya.neryaallnaturals.entity.Customer;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressService {

    private final AddressRepository addressRepository;
    private final CustomerService customerService;

    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses(String username) {
        Customer customer = customerService.getCustomerByUsername(username);
        return addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(customer.getId()).stream()
                .map(AddressResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse createAddress(String username, AddressRequest request) {
        Customer customer = customerService.getCustomerByUsername(username);
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());

        Address address = Address.builder()
                .customer(customer)
                .name(request.getName())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .pinCode(request.getPinCode())
                .isDefault(makeDefault)
                .build();

        if (makeDefault) {
            clearExistingDefault(customer.getId());
        }

        Address saved = addressRepository.save(address);
        log.info("Created address {} for customer {}", saved.getId(), customer.getId());
        return AddressResponse.fromEntity(saved);
    }

    @Transactional
    public AddressResponse updateAddress(String username, Long addressId, AddressRequest request) {
        Address address = findOwnedAddress(username, addressId);

        address.setName(request.getName());
        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPinCode(request.getPinCode());

        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            clearExistingDefault(address.getCustomer().getId());
        }
        address.setIsDefault(makeDefault);

        Address updated = addressRepository.save(address);
        return AddressResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteAddress(String username, Long addressId) {
        Address address = findOwnedAddress(username, addressId);
        addressRepository.delete(address);
        log.info("Deleted address {}", addressId);
    }

    /**
     * Look up an address by id, scoped to the caller's own customer row in the same query
     * — an address belonging to another customer is indistinguishable from a non-existent
     * one, so this never leaks whether the id exists.
     */
    private Address findOwnedAddress(String username, Long addressId) {
        Customer customer = customerService.getCustomerByUsername(username);
        return addressRepository.findByIdAndCustomerId(addressId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }

    private void clearExistingDefault(Long customerId) {
        addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(customerId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                .forEach(a -> {
                    a.setIsDefault(false);
                    addressRepository.save(a);
                });
    }
}
