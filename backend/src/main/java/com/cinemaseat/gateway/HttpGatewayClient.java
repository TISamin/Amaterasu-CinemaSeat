package com.cinemaseat.gateway;

import com.cinemaseat.config.GatewayProperties;
import com.cinemaseat.payment.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Thin JDK {@link HttpClient} wrapper around the provided mock gateway.
 *
 * <ul>
 *   <li>Always sends {@code Idempotency-Key} on /charge so retries collapse.</li>
 *   <li>Optional {@code X-Mock-Force} for the gateway's deterministic modes
 *       (success, fail, duplicate, timeout, race).</li>
 *   <li>Timeouts via {@code HttpClient.Builder.connectTimeout(...)} AND
 *       {@code HttpRequest.Builder.timeout(...)} so a hung gateway never
 *       blocks the request thread forever.</li>
 *   <li>Non-2xx → {@link GatewayException}; timeout →
 *       {@link GatewayTimeoutException}; never crash the JVM.</li>
 * </ul>
 */
@Component
public class HttpGatewayClient implements GatewayClient {

    private final GatewayProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpGatewayClient(GatewayProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(props.getTimeoutMs(), 2_000L)))
                .build();
    }

    // -- /charge ----------------------------------------------------------

    @Override
    public ChargeResponse charge(ChargeRequest req, String idempotencyKey, String mockForce) {
        String body;
        try {
            body = mapper.writeValueAsString(new ChargeBody(
                    req.amount(),
                    req.currency(),
                    req.bookingRef(),
                    req.callbackUrl()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise /charge body", e);
        }

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(props.getGatewayUrl() + "/charge"))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey);
        if (mockForce != null && !mockForce.isBlank()) {
            b.header("X-Mock-Force", mockForce);
        }
        HttpRequest httpReq = b.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();

        JsonNode json = execute(httpReq, "/charge");
        // Expected: { "payment_id": "pay_...", "status": "PENDING" }
        String pid = textOrNull(json, "payment_id");
        if (pid == null || pid.isBlank()) {
            throw new GatewayException("gateway /charge missing payment_id", 502, json.toString());
        }
        return new ChargeResponse(pid, PaymentStatus.PENDING);
    }

    // -- /refund ----------------------------------------------------------

    @Override
    public RefundResponse refund(String paymentId, BigDecimal amount, String reason, String mockForce) {
        String body;
        try {
            body = mapper.writeValueAsString(new RefundBody(paymentId, amount, reason));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise /refund body", e);
        }
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(props.getGatewayUrl() + "/refund"))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json");
        if (mockForce != null && !mockForce.isBlank()) {
            b.header("X-Mock-Force", mockForce);
        }
        HttpRequest httpReq = b.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();

        JsonNode json = execute(httpReq, "/refund");
        String rid = textOrNull(json, "refund_id");
        if (rid == null || rid.isBlank()) {
            throw new GatewayException("gateway /refund missing refund_id", 502, json.toString());
        }
        return new RefundResponse(rid, textOrNull(json, "status"));
    }

    // -- /otp/send --------------------------------------------------------

    @Override
    public OtpSendResponse otpSend(String phone, String ref) {
        String body;
        try {
            body = mapper.writeValueAsString(new OtpSendBody(phone, ref));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise /otp/send body", e);
        }
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(props.getGatewayUrl() + "/otp/send"))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        JsonNode json = execute(httpReq, "/otp/send");
        boolean ok = json.path("ok").asBoolean(false);
        return new OtpSendResponse(ok, textOrNull(json, "session_ref"), textOrNull(json, "error"));
    }

    // -- /otp/verify ------------------------------------------------------

    @Override
    public OtpVerifyResponse otpVerify(String ref, String code) {
        String body;
        try {
            body = mapper.writeValueAsString(new OtpVerifyBody(ref, code));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise /otp/verify body", e);
        }
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(props.getGatewayUrl() + "/otp/verify"))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        JsonNode json = execute(httpReq, "/otp/verify");
        return new OtpVerifyResponse(json.path("verified").asBoolean(false));
    }

    // -- transport --------------------------------------------------------

    private JsonNode execute(HttpRequest httpReq, String label) {
        HttpResponse<String> res;
        try {
            res = http.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new GatewayTimeoutException("gateway timeout on " + label, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayTimeoutException("interrupted while calling gateway " + label, e);
        } catch (IOException e) {
            throw new GatewayException("transport error calling gateway " + label, 502, e.getMessage());
        }
        int sc = res.statusCode();
        String body = res.body() == null ? "" : res.body();
        if (sc < 200 || sc >= 300) {
            throw new GatewayException("gateway " + label + " returned " + sc, sc, body);
        }
        if (body.isBlank()) return mapper.createObjectNode();
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new GatewayException("gateway " + label + " returned non-JSON", sc, body);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    // -- request bodies (record classes are easier to feed Jackson) -------

    private record ChargeBody(BigDecimal amount, String currency, String booking_ref, String callback_url) {}
    private record RefundBody(String payment_id, BigDecimal amount, String reason) {}
    private record OtpSendBody(String phone, String ref) {}
    private record OtpVerifyBody(String ref, String code) {}
}