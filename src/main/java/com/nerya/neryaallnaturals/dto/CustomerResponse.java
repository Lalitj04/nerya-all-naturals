package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {

    private Long id;
    private String customerName;
    private String customerPhone;
    private LocalDate dateOfBirth;
    private Customer.Gender gender;
    private String username;
    private String email;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CustomerResponse fromEntity(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .customerName(customer.getCustomerName())
                .customerPhone(customer.getCustomerPhone())
                .dateOfBirth(customer.getDateOfBirth())
                .gender(customer.getGender())
                .username(customer.getUser().getUsername())
                .email(customer.getUser().getEmail())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
