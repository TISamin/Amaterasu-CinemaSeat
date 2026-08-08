package com.cinemaseat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads {@code HOLD_TTL_SECONDS} from the environment. Never hardcode the TTL.
 * (DATABASE_CONTRACT §10, STATE_MACHINE §1)
 */
@Component
public class HoldProperties {

    private final long ttlSeconds;

    public HoldProperties(@Value("${hold.ttl-seconds:120}") long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalStateException(
                "HOLD_TTL_SECONDS must be positive, got " + ttlSeconds);
        }
        this.ttlSeconds = ttlSeconds;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }
}