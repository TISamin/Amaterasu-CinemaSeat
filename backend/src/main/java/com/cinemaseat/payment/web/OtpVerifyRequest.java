package com.cinemaseat.payment.web;

public record OtpVerifyRequest(String ref, String code) {}