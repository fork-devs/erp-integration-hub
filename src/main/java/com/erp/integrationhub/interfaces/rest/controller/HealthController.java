package com.erp.integrationhub.interfaces.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Health & Root", description = "Public liveness, readiness probes and platform root ping")
public class HealthController {

    @GetMapping("/")
    @Operation(summary = "Platform root ping", description = "Verifies application is running and accessible")
    public ResponseEntity<Map<String, String>> rootPing() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "app", "ShiftIQ Platform API",
                "docs", "/swagger-ui.html"
        ));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check probe", description = "Standard health check for container orchestrators")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP"
        ));
    }
}
