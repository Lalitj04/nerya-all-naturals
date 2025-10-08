package com.nerya.neryaallnaturals.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address extends BaseEntity {

    @NotNull(message = "Customer ID is required")
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255)
    @Column(name = "address_line_1", nullable = false)
    private String addressLine1;

    @Size(max = 255)
    @Column(name = "address_line_2")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    @Column(name = "city", nullable = false)
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100)
    @Column(name = "state", nullable = false)
    private String state;

    @NotBlank(message = "Pin code is required")
    @Size(max = 20)
    @Column(name = "pin_code", nullable = false)
    private String pinCode;
}