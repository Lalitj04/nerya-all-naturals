package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.CustomerProfileUpdateRequest;
import com.nerya.neryaallnaturals.dto.CustomerResponse;
import com.nerya.neryaallnaturals.dto.RegisterRequest;
import com.nerya.neryaallnaturals.dto.UserRequest;
import com.nerya.neryaallnaturals.entity.Customer;
import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserService userService;

    /**
     * Create a User (ROLE_CUSTOMER) and its linked Customer row in one transaction.
     *
     * @param request registration details
     * @return the persisted customer, with its user populated
     */
    @Transactional
    public Customer registerCustomer(RegisterRequest request) {
        if (userService.usernameExists(request.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        if (userService.emailExists(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        UserRequest userRequest = UserRequest.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .roles(Set.of(User.Role.ROLE_CUSTOMER))
                .build();

        User user = userService.createUser(userRequest);

        Customer customer = Customer.builder()
                .user(user)
                .customerName(request.getFirstName() + " " + request.getLastName())
                .customerPhone(request.getPhoneNumber())
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Registered new customer '{}' (user id {})", user.getUsername(), user.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public CustomerResponse getMyProfile(String username) {
        return CustomerResponse.fromEntity(getCustomerByUsername(username));
    }

    @Transactional
    public CustomerResponse updateMyProfile(String username, CustomerProfileUpdateRequest request) {
        Customer customer = getCustomerByUsername(username);

        if (request.getCustomerName() != null) {
            customer.setCustomerName(request.getCustomerName());
        }
        if (request.getCustomerPhone() != null) {
            customer.setCustomerPhone(request.getCustomerPhone());
        }
        if (request.getDateOfBirth() != null) {
            customer.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender() != null) {
            customer.setGender(request.getGender());
        }

        Customer updated = customerRepository.save(customer);
        return CustomerResponse.fromEntity(updated);
    }

    /**
     * Resolve the Customer owned by the authenticated principal. Never accepts a path/body
     * id — the caller can only ever act on their own customer row.
     */
    @Transactional(readOnly = true)
    public Customer getCustomerByUsername(String username) {
        return customerRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }

    /**
     * @return the customer id for a username, or empty for users with no customer profile
     * (e.g. admins) — used when building login/register responses.
     */
    @Transactional(readOnly = true)
    public Optional<Long> findCustomerIdByUsername(String username) {
        return customerRepository.findByUserUsername(username).map(Customer::getId);
    }
}
