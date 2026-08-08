package com.cinemaseat.payment.web;

import com.cinemaseat.payment.gateway.GatewayClient;
import com.cinemaseat.payment.gateway.GatewayOtpSendRequest;
import com.cinemaseat.payment.gateway.GatewayOtpSendResponse;
import com.cinemaseat.payment.gateway.GatewayOtpVerifyRequest;
import com.cinemaseat.payment.gateway.GatewayOtpVerifyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forwards OTP requests to the provided gateway.
 *
 * <p>{@code /otp/send} is best-effort — the gateway may silently fail to deliver
 * (API_CONTRACT §9). {@code /otp/verify} returns the gateway's verdict.</p>
 */
@RestController
@RequestMapping("/api/otp")
public class OtpController {

    private final GatewayClient gateway;

    public OtpController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/send")
    public ResponseEntity<GatewayOtpSendResponse> send(@RequestBody OtpSendRequest req) {
        if (req == null || req.phone() == null || req.ref() == null) {
            return ResponseEntity.badRequest().build();
        }
        GatewayOtpSendResponse resp = gateway.otpSend(
                new GatewayOtpSendRequest(req.phone(), req.ref()));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifyResponse> verify(@RequestBody OtpVerifyRequest req) {
        if (req == null || req.ref() == null || req.code() == null) {
            return ResponseEntity.badRequest().build();
        }
        GatewayOtpVerifyResponse resp = gateway.otpVerify(
                new GatewayOtpVerifyRequest(req.ref(), req.code()));
        return ResponseEntity.ok(new OtpVerifyResponse(resp.verified()));
    }
}