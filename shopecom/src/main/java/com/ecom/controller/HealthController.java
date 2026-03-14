package com.ecom.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired
    private DataSource dataSource;

    // ── Kubernetes liveness probe ──
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ShopEcom",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // ── Kubernetes readiness probe — checks DB connection ──
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            if (valid) {
                return ResponseEntity.ok(Map.of(
                        "status", "READY",
                        "database", "UP",
                        "timestamp", LocalDateTime.now().toString()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "NOT READY",
                    "database", "DOWN",
                    "error", e.getMessage()
            ));
        }
        return ResponseEntity.status(503).body(Map.of("status", "NOT READY"));
    }
}
