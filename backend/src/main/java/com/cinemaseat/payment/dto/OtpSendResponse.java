package com.cinemaseat.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpSendResponse {

    @JsonProperty("ok")
    private boolean ok;

    @JsonProperty("session_ref")
    private String sessionRef;

    @JsonProperty("error")
    private String error;

    public OtpSendResponse() {}

    public OtpSendResponse(boolean ok, String sessionRef, String error) {
        this.ok = ok;
        this.sessionRef = sessionRef;
        this.error = error;
    }

    public boolean isOk() { return ok; }
    public String getSessionRef() { return sessionRef; }
    public String getError() { return error; }

    public void setOk(boolean ok) { this.ok = ok; }
    public void setSessionRef(String sessionRef) { this.sessionRef = sessionRef; }
    public void setError(String error) { this.error = error; }
}
