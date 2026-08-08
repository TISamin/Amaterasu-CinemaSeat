package com.cinemaseat.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight health endpoint. Must NOT call the gateway and must remain 200
 * even when the gateway is down (Spec §22). Lives at {@code /health} not
 * {@code /api/health} for judge compatibility.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}