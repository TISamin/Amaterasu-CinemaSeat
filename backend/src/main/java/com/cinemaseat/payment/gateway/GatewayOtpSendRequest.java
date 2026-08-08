package com.cinemaseat.payment.gateway;

public record GatewayOtpSendRequest(String phone, String ref) {}