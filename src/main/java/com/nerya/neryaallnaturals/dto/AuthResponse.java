package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;

    @Builder.Default
    private String type = "Bearer";

    private String refreshToken;
    private String username;
    private String email;
    private Set<User.Role> roles;

    /**
     * Null for users without a customer profile (e.g. admins).
     */
    private Long customerId;
}
