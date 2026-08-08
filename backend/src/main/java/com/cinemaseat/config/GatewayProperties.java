package com.cinemaseat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gateway connection settings. Read from {@code application.yml} so
 * the docker-compose override can point at {@code http://gateway:9000}
 * while local dev keeps {@code http://localhost:9000}.
 */
@Component
public class GatewayProperties {

    private final String url;
    private final String secret;
    private final long timeoutMs;

    public GatewayProperties(
            @Value("${gateway.url:http://gateway:9000}") String url,
            @Value("${gateway.secret:}") String secret,
            @Value("${gateway.timeout-ms:5000}") long timeoutMs) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("gateway.url must not be blank");
        }
        if (timeoutMs <= 0) {
            throw new IllegalStateException("gateway.timeout-ms must be positive");
        }
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.secret = secret == null ? "" : secret;
        this.timeoutMs = timeoutMs;
    }

    public String getUrl() { return url; }
    public String getSecret() { return secret; }
    public long getTimeoutMs() { return timeoutMs; }

    public String chargeEndpoint()   { return url + "/charge"; }
    public String otpSendEndpoint()  { return url + "/otp/send"; }
    public String otpVerifyEndpoint(){ return url + "/otp/verify"; }
}