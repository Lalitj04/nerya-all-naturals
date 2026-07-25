package com.nerya.neryaallnaturals.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nerya.neryaallnaturals.config.RazorpayProperties;
import com.nerya.neryaallnaturals.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client for the Razorpay Orders API and webhook signature verification (T54). Talks to the
 * gateway over plain REST rather than pulling in the full Razorpay SDK.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayGatewayService {

    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final RazorpayProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Create a provider-side order for the given amount (in the smallest currency unit). */
    public String createOrder(BigDecimal amount, String currency, String receipt) {
        if (properties.getKeyId() == null || properties.getKeyId().isBlank()) {
            throw new ConflictException("Online payment is not configured");
        }

        long amountInSmallestUnit = amount.movePointRight(2).longValueExact();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountInSmallestUnit);
        body.put("currency", currency);
        body.put("receipt", receipt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret());

        try {
            JsonNode response = restTemplate.exchange(
                    ORDERS_URL, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class).getBody();
            return response.get("id").asText();
        } catch (RestClientException ex) {
            log.error("Razorpay order creation failed for receipt {}", receipt, ex);
            throw new ConflictException("Could not initiate online payment; please try again");
        }
    }

    /** Verify the {@code X-Razorpay-Signature} header for a raw webhook body (T54). */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || properties.getWebhookSecret() == null
                || properties.getWebhookSecret().isBlank()) {
            return false;
        }
        String expected = hmacSha256Hex(rawBody, properties.getWebhookSecret());
        return constantTimeEquals(expected, signatureHeader);
    }

    public JsonNode parse(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new ConflictException("Malformed webhook payload");
        }
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute webhook signature", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
