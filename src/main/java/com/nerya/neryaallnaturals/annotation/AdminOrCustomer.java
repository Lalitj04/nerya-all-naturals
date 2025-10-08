package com.nerya.neryaallnaturals.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to restrict access to ADMIN or CUSTOMER roles
 * Usage: @AdminOrCustomer on controller methods
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
public @interface AdminOrCustomer {
}
