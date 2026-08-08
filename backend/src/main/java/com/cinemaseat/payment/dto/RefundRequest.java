package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefundRequest {

    @JsonProperty("payment_id")
    private String paymentId;

    @JsonProperty("amount")
    private Integer amount;

    @JsonProperty("reason")
    private String reason;

    public RefundRequest() {}

    public RefundRequest(String paymentId, Integer amount, String reason) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
    }

    public String getPaymentId() { return paymentId; }
    public Integer getAmount() { return amount; }
    public String getReason() { return reason; }

    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public void setReason(String reason) { this.reason = reason; }
}
