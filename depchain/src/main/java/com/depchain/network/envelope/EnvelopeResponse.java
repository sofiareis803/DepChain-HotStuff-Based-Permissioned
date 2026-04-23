package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EnvelopeResponse extends Envelope {
    @JsonProperty("message")
    private String message;

    @JsonProperty("request_nonce")
    private long requestNonce;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getRequestNonce() { return requestNonce; }
    public void setRequestNonce(long requestNonce) { this.requestNonce = requestNonce; }
}