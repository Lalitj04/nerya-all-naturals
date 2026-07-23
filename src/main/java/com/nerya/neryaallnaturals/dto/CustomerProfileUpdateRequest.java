package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.Customer;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfileUpdateRequest {

    @Size(max = 100)
    private String customerName;

    @Size(max = 20)
    private String customerPhone;

    private LocalDate dateOfBirth;

    private Customer.Gender gender;
}
