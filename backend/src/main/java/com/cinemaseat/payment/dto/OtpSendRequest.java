package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpSendRequest {

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("ref")
    private String ref;

    public OtpSendRequest() {}

    public OtpSendRequest(String phone, String ref) {
        this.phone = phone;
        this.ref = ref;
    }

    public String getPhone() { return phone; }
    public String getRef() { return ref; }

    public void setPhone(String phone) { this.phone = phone; }
    public void setRef(String ref) { this.ref = ref; }
}
