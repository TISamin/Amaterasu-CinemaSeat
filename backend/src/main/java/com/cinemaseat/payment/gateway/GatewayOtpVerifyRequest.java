package com.cinemaseat.payment.gateway;

public record GatewayOtpVerifyRequest(String ref, String code) {}