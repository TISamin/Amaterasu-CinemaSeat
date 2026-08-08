package com.cinemaseat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gateway connection settings, read from environment variables that the
 * docker-compose file wires into the api container (see docker-compose.yml):
 * <ul>
 *   <li>{@code GATEWAY_URL} — full base URL, e.g. {@code http://gateway:9000}.
 *       Inside Docker this MUST use the {@code gateway} service name, never
 *       {@code localhost}.</li>
 *   <li>{@code GATEWAY_TIMEOUT_MS} — HTTP timeout for /charge. {@code /charge}
 *       is the only call that should ever approach it; OTP calls are quick.</li>
 *   <li>{@code CALLBACK_URL} — absolute URL the gateway should POST callbacks
 *       to. Composed in docker-compose as
 *       {@code http://api:${API_PORT}/api/payments/callback}.</li>
 *   <li>{@code GATEWAY_SECRET} — optional shared secret for HMAC-SHA256
 *       verification of the X-Signature header. Disabled when blank.</li>
 * </ul>
 *
 * <p>Variable names mirror what the merged docker-compose.yml already wires;
 * none of Agent 2's Node-era variable names ({@code GATEWAY_BASE_URL},
 * {@code BACKEND_SERVICE_NAME}, {@code HMAC_ENABLED}, {@code SIGNATURE_ENCODING},
 * {@code GATEWAY_PATH_PREFIX}, {@code API_HOST}, {@code OTP_DETERMINISTIC})
 * are read.
 */
@Component
public class GatewayProperties {

    private final String gatewayUrl;
    private final int timeoutMs;
    private final String callbackUrl;
    private final String secret;

    public GatewayProperties(
            @Value("${gateway.url:http://gateway:9000}") String gatewayUrl,
            @Value("${gateway.timeout-ms:5000}") int timeoutMs,
            @Value("${gateway.callback-url:http://api:8080/api/payments/callback}") String callbackUrl,
            @Value("${gateway.secret:}") String secret) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new IllegalStateException("GATEWAY_URL must not be blank");
        }
        if (timeoutMs <= 0) {
            throw new IllegalStateException("GATEWAY_TIMEOUT_MS must be positive, got " + timeoutMs);
        }
        this.gatewayUrl = stripTrailingSlash(gatewayUrl);
        this.timeoutMs = timeoutMs;
        this.callbackUrl = callbackUrl;
        this.secret = (secret == null) ? "" : secret;
    }

    public String getGatewayUrl() { return gatewayUrl; }
    public int getTimeoutMs() { return timeoutMs; }
    public String getCallbackUrl() { return callbackUrl; }

    /** {@code true} when a non-blank GATEWAY_SECRET is configured. */
    public boolean isHmacEnabled() { return !secret.isBlank(); }

    public String getSecret() { return secret; }

    private static String stripTrailingSlash(String s) {
        String r = s;
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r;
    }
}