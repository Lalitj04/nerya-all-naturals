package com.nerya.neryaallnaturals.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Login brute-force throttle (T58). Configurable so tests can raise the limit or tighten it. */
@Component
@ConfigurationProperties(prefix = "rate-limit.login")
@Getter
@Setter
public class LoginRateLimitProperties {

    private int capacity = 5;
    private int windowSeconds = 60;
}
