package com.nerya.neryaallnaturals.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment.razorpay")
@Getter
@Setter
public class RazorpayProperties {

    /** Public key id, sent to the checkout widget. Empty disables the gateway. */
    private String keyId;

    /** Secret key, used to authenticate server-to-server order-creation calls. */
    private String keySecret;

    /** Secret configured on the Razorpay webhook, used to verify inbound signatures. */
    private String webhookSecret;
}
