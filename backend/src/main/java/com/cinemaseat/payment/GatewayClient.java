package com.cinemaseat.payment;

import com.cinemaseat.payment.dto.ChargeRequest;
import com.cinemaseat.payment.dto.ChargeResponse;
import com.cinemaseat.payment.dto.OtpSendRequest;
import com.cinemaseat.payment.dto.OtpSendResponse;
import com.cinemaseat.payment.dto.OtpVerifyRequest;
import com.cinemaseat.payment.dto.OtpVerifyResponse;

public interface GatewayClient {
    ChargeResponse charge(ChargeRequest req, String idempotencyKey);
    OtpSendResponse otpSend(OtpSendRequest req);
    OtpVerifyResponse otpVerify(OtpVerifyRequest req);
}
