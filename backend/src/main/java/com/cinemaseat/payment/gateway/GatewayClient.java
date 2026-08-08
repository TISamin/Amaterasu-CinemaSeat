package com.cinemaseat.payment.gateway;

/**
 * Contract for talking to the payment gateway.
 *
 * Three operations:
 * <ul>
 *   <li>{@link #charge} — initiate a charge, returns gateway's payment_id.</li>
 *   <li>{@link #otpSend} — best-effort OTP delivery; never throws.</li>
 *   <li>{@link #otpVerify} — best-effort OTP verification; never throws.</li>
 * </ul>
 *
 * Defined as an interface so unit tests can mock it without dragging in
 * ByteBuddy / RestClient instrumentation. The production implementation is
 * {@link RestGatewayClient}.
 */
public interface GatewayClient {

    GatewayChargeResponse charge(GatewayChargeRequest req);

    GatewayOtpSendResponse otpSend(GatewayOtpSendRequest req);

    GatewayOtpVerifyResponse otpVerify(GatewayOtpVerifyRequest req);
}
