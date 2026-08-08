package com.cinemaseat.payment;

import com.cinemaseat.payment.dto.OtpSendRequest;
import com.cinemaseat.payment.dto.OtpSendResponse;
import com.cinemaseat.payment.dto.OtpVerifyRequest;
import com.cinemaseat.payment.dto.OtpVerifyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/otp")
public class OtpController {

    private final PaymentService paymentService;

    public OtpController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/send")
    public ResponseEntity<OtpSendResponse> sendOtp(@RequestBody OtpSendRequest req) {
        OtpSendResponse response = paymentService.sendOtp(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(@RequestBody OtpVerifyRequest req) {
        OtpVerifyResponse response = paymentService.verifyOtp(req);
        return ResponseEntity.ok(response);
    }
}
