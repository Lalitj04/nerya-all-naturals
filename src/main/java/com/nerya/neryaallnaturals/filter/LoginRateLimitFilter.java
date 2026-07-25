package com.nerya.neryaallnaturals.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nerya.neryaallnaturals.config.LoginRateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Login brute-force protection (T58, fix V6): 5 failed attempts/minute per client IP AND per
 * attempted username/email, whichever limit is hit first. Each bucket is independent so one
 * abusive IP cycling through usernames, or one username hammered from many IPs, is still
 * throttled. Only failed attempts consume the budget — a correct password never counts against
 * a legitimate user sharing an IP with others (e.g. behind NAT/a proxy).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimitProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> usernameBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        CachedBodyRequest wrapped = new CachedBodyRequest(request, body);

        String ip = clientIp(request);
        String username = extractUsername(body);

        Bucket ipBucket = ipBuckets.computeIfAbsent(ip, k -> newBucket());
        Bucket usernameBucket = username == null ? null
                : usernameBuckets.computeIfAbsent(username, k -> newBucket());

        if (ipBucket.getAvailableTokens() <= 0 || (usernameBucket != null && usernameBucket.getAvailableTokens() <= 0)) {
            log.warn("Login rate limit exceeded for ip={} username={}", ip, username);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many login attempts. Try again in a minute.\"}");
            return;
        }

        filterChain.doFilter(wrapped, response);

        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            ipBucket.tryConsume(1);
            if (usernameBucket != null) {
                usernameBucket.tryConsume(1);
            }
        }
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI());
    }

    private Bucket newBucket() {
        int capacity = properties.getCapacity();
        Duration window = Duration.ofSeconds(properties.getWindowSeconds());
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, window).build())
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUsername(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String value = node.path("usernameOrEmail").asText(null);
            return value == null ? null : value.toLowerCase();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Replays the already-consumed body so the controller's {@code @RequestBody} binding still works. */
    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return source.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
