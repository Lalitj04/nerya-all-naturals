package com.nerya.neryaallnaturals.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @NotBlank(message = "Customer name is required")
    @Size(max = 100)
    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Size(max = 20)
    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    public enum Gender {
        MALE,
        FEMALE,
        OTHER,
        PREFER_NOT_TO_SAY
    }
}
