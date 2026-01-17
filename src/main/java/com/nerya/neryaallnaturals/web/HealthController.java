package com.nerya.neryaallnaturals.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${spring.application.name:nerya-all-naturals}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Health check endpoint - Open API (No authentication required)
     * Use this endpoint to verify if the application is deployed and running
     * 
     * @return deployment status information
     */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", applicationName);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("port", serverPort);
        response.put("message", "Application is deployed and running successfully");
        return response;
    }

    /**
     * Simple ping endpoint - Open API (No authentication required)
     * Quick check to verify if the application is responding
     * 
     * @return simple response
     */
    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "status", "OK",
            "message", "pong",
            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
}


