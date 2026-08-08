package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefundResponse {

    @JsonProperty("refund_id")
    private String refundId;

    @JsonProperty("status")
    private String status;

    public RefundResponse() {}

    public RefundResponse(String refundId, String status) {
        this.refundId = refundId;
        this.status = status;
    }

    public String getRefundId() { return refundId; }
    public String getStatus() { return status; }

    public void setRefundId(String refundId) { this.refundId = refundId; }
    public void setStatus(String status) { this.status = status; }
}
