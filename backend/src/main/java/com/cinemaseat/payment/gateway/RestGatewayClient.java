package com.cinemaseat.payment.gateway;

import com.cinemaseat.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Production {@link GatewayClient} backed by Spring's {@link RestClient}.
 *
 * Contract: gateway exposes {@code POST /charge}, {@code POST /otp/send},
 * {@code POST /otp/verify}. Each method returns the parsed body or throws
 * {@link GatewayException} on any non-2xx / network failure.
 *
 * All call sites must treat {@link GatewayException} as transient. The booking
 * row stays {@code PENDING_PAYMENT} and {@code /pay} should bubble the error up
 * as 502 Bad Gateway.
 */
@Component
public class RestGatewayClient implements GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(RestGatewayClient.class);

    private final GatewayProperties props;
    private final RestClient http;

    public RestGatewayClient(GatewayProperties props) {
        this.props = props;
        RestClient.Builder b = RestClient.builder()
                .baseUrl(props.getUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (!props.getSecret().isBlank()) {
            b.defaultHeader("X-Gateway-Secret", props.getSecret());
        }
        this.http = b.build();
    }

    @Override
    public GatewayChargeResponse charge(GatewayChargeRequest req) {
        try {
            GatewayChargeResponse resp = http.post()
                    .uri(props.chargeEndpoint())
                    .body(req)
                    .retrieve()
                    .body(GatewayChargeResponse.class);
            if (resp == null || resp.paymentId() == null || resp.paymentId().isBlank()) {
                throw new GatewayException("Gateway returned no payment_id for bookingRef="
                        + req.bookingRef());
            }
            log.info("GATEWAY_CHARGE bookingRef={} paymentId={}", req.bookingRef(), resp.paymentId());
            return resp;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.warn("GATEWAY_CHARGE_HTTP_ERROR bookingRef={} status={} body={}",
                    req.bookingRef(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new GatewayException("Gateway rejected charge: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            log.warn("GATEWAY_CHARGE_TIMEOUT bookingRef={} cause={}", req.bookingRef(), e.getMessage());
            throw new GatewayException("Gateway unreachable: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayOtpSendResponse otpSend(GatewayOtpSendRequest req) {
        try {
            GatewayOtpSendResponse resp = http.post()
                    .uri(props.otpSendEndpoint())
                    .body(req)
                    .retrieve()
                    .body(GatewayOtpSendResponse.class);
            return resp == null ? new GatewayOtpSendResponse(false) : resp;
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            // OTP send is best-effort per API contract §9. Swallow but log.
            log.warn("GATEWAY_OTP_SEND_FAILED ref={} cause={}", req.ref(), e.toString());
            return new GatewayOtpSendResponse(false);
        }
    }

    @Override
    public GatewayOtpVerifyResponse otpVerify(GatewayOtpVerifyRequest req) {
        try {
            GatewayOtpVerifyResponse resp = http.post()
                    .uri(props.otpVerifyEndpoint())
                    .body(req)
                    .retrieve()
                    .body(GatewayOtpVerifyResponse.class);
            return resp == null ? new GatewayOtpVerifyResponse(false) : resp;
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            log.warn("GATEWAY_OTP_VERIFY_FAILED ref={} cause={}", req.ref(), e.toString());
            return new GatewayOtpVerifyResponse(false);
        }
    }
}
