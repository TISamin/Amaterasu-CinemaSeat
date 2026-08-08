package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpVerifyRequest {

    @JsonProperty("ref")
    private String ref;

    @JsonProperty("code")
    private String code;

    public OtpVerifyRequest() {}

    public OtpVerifyRequest(String ref, String code) {
        this.ref = ref;
        this.code = code;
    }

    public String getRef() { return ref; }
    public String getCode() { return code; }

    public void setRef(String ref) { this.ref = ref; }
    public void setCode(String code) { this.code = code; }
}
