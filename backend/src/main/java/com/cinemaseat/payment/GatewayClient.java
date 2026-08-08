package com.cinemaseat.payment;

import com.cinemaseat.payment.dto.ChargeRequest;
import com.cinemaseat.payment.dto.ChargeResponse;
import com.cinemaseat.payment.dto.OtpSendRequest;
import com.cinemaseat.payment.dto.OtpSendResponse;
import com.cinemaseat.payment.dto.OtpVerifyRequest;
import com.cinemaseat.payment.dto.OtpVerifyResponse;
import com.cinemaseat.payment.dto.RefundRequest;
import com.cinemaseat.payment.dto.RefundResponse;

public interface GatewayClient {
    ChargeResponse charge(ChargeRequest req, String idempotencyKey);
    ChargeResponse charge(ChargeRequest req, String idempotencyKey, String mockForce, String mockMode);
    RefundResponse refund(RefundRequest req, String mockForce);
    OtpSendResponse otpSend(OtpSendRequest req);
    OtpVerifyResponse otpVerify(OtpVerifyRequest req);
}
