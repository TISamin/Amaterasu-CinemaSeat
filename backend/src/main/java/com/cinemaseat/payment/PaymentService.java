package com.cinemaseat.payment;

import com.cinemaseat.payment.dto.CallbackPayload;
import com.cinemaseat.payment.dto.OtpSendRequest;
import com.cinemaseat.payment.dto.OtpSendResponse;
import com.cinemaseat.payment.dto.OtpVerifyRequest;
import com.cinemaseat.payment.dto.OtpVerifyResponse;
import com.cinemaseat.payment.dto.RefundResponse;

public interface PaymentService {

    Payment initiatePayment(String bookingRef);

    Payment initiatePayment(String bookingRef, String mockForce, String mockMode);

    RefundResponse initiateRefund(String paymentId, String mockForce);

    /**
     * Processes gateway callback with HMAC signature verification over raw request body.
     */
    boolean processCallback(byte[] rawBodyBytes, String signatureHeader);

    boolean processCallback(CallbackPayload payload);

    OtpSendResponse sendOtp(OtpSendRequest req);

    OtpVerifyResponse verifyOtp(OtpVerifyRequest req);
}
