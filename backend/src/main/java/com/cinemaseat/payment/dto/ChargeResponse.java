package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChargeResponse {

    @JsonProperty("payment_id")
    private String paymentId;

    @JsonProperty("status")
    private String status;

    public ChargeResponse() {}

    public ChargeResponse(String paymentId, String status) {
        this.paymentId = paymentId;
        this.status = status;
    }

    public String getPaymentId() { return paymentId; }
    public String getStatus() { return status; }

    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public void setStatus(String status) { this.status = status; }
}
