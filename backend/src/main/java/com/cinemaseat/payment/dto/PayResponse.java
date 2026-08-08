package com.cinemaseat.payment.dto;

public class PayResponse {

    private String bookingRef;
    private String paymentId;
    private String status;

    public PayResponse() {}

    public PayResponse(String bookingRef, String paymentId, String status) {
        this.bookingRef = bookingRef;
        this.paymentId = paymentId;
        this.status = status;
    }

    public String getBookingRef() { return bookingRef; }
    public String getPaymentId() { return paymentId; }
    public String getStatus() { return status; }

    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public void setStatus(String status) { this.status = status; }
}
