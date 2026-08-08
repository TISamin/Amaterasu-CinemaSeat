package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpSendRequest {

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("ref")
    private String ref;

    @JsonProperty("callback_url")
    private String callbackUrl;

    public OtpSendRequest() {}

    public OtpSendRequest(String phone, String ref) {
        this.phone = phone;
        this.ref = ref;
    }

    public OtpSendRequest(String phone, String ref, String callbackUrl) {
        this.phone = phone;
        this.ref = ref;
        this.callbackUrl = callbackUrl;
    }

    public String getPhone() { return phone; }
    public String getRef() { return ref; }
    public String getCallbackUrl() { return callbackUrl; }

    public void setPhone(String phone) { this.phone = phone; }
    public void setRef(String ref) { this.ref = ref; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
}
