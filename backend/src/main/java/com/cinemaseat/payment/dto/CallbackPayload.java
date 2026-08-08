package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CallbackPayload {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("payment_id")
    private String paymentId;

    @JsonProperty("booking_ref")
    private String bookingRef;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private Integer amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("timestamp")
    private String timestamp;

    public CallbackPayload() {}

    public CallbackPayload(String eventId, String paymentId, String bookingRef, String status, Integer amount, String currency, String timestamp) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.bookingRef = bookingRef;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public String getPaymentId() { return paymentId; }
    public String getBookingRef() { return bookingRef; }
    public String getStatus() { return status; }
    public Integer getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getTimestamp() { return timestamp; }

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public void setStatus(String status) { this.status = status; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
