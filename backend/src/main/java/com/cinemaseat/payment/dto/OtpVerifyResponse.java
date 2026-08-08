package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpVerifyResponse {

    @JsonProperty("verified")
    private boolean verified;

    public OtpVerifyResponse() {}

    public OtpVerifyResponse(boolean verified) {
        this.verified = verified;
    }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
