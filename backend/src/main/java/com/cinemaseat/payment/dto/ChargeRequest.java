package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChargeRequest {

    @JsonProperty("amount")
    private Integer amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("booking_ref")
    private String bookingRef;

    @JsonProperty("callback_url")
    private String callbackUrl;

    public ChargeRequest() {}

    public ChargeRequest(Integer amount, String currency, String bookingRef, String callbackUrl) {
        this.amount = amount;
        this.currency = currency;
        this.bookingRef = bookingRef;
        this.callbackUrl = callbackUrl;
    }

    public Integer getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getBookingRef() { return bookingRef; }
    public String getCallbackUrl() { return callbackUrl; }

    public void setAmount(Integer amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
}
