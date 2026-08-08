package com.cinemaseat.payment;

import com.cinemaseat.payment.dto.CallbackPayload;
import com.cinemaseat.payment.dto.OtpSendRequest;
import com.cinemaseat.payment.dto.OtpSendResponse;
import com.cinemaseat.payment.dto.OtpVerifyRequest;
import com.cinemaseat.payment.dto.OtpVerifyResponse;

public interface PaymentService {

    Payment initiatePayment(String bookingRef);

    /**
     * Processes gateway callback.
     * Returns true if processing completed (whether first time or recognized duplicate).
     */
    boolean processCallback(CallbackPayload payload);

    OtpSendResponse sendOtp(OtpSendRequest req);

    OtpVerifyResponse verifyOtp(OtpVerifyRequest req);
}
