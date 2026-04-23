package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class Envelope {
        
    @JsonProperty("sender_id")
    private long senderId;

    @JsonProperty("hmac")
    private String hmac;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("nonce")
    private long nonce;

    @JsonProperty("format_version")
    private int formatVersion = 1;

    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }

    public String getHmac() {  return hmac;}
    public void setHmac(String hmac) { this.hmac = hmac; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getNonce() {  return nonce; }
    public void setNonce(long nonce) { this.nonce = nonce; }

    public int getFormatVersion() {  return formatVersion;}
}