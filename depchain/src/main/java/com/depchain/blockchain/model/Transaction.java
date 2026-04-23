package com.depchain.blockchain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Transaction {
    @JsonProperty("sender")
    private String sender;
    
    @JsonProperty("dest")
    private String dest;

    @JsonProperty("value")
    private long value; // DepCoin

    @JsonProperty("call_data")
    private String callData; // input

    @JsonProperty("gas_limit")
    private long gasLimit; 

    @JsonProperty("gas_price")
    private long gasPrice; 

    @JsonProperty("nonce")
    private long nonce;

    @JsonProperty("signature")
    private String signature;


    public Transaction() {} // Default constructor for JSON deserialization

    @JsonIgnore
    public String getPayloadToSign() {
        return String.format("%s:%s:%d:%d:%d:%d:%s", sender, dest, value, nonce, gasLimit, gasPrice, (callData == null ? "" : callData));
    }

    @JsonIgnore
    public String getTransactionHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // hash the payload + the signature 
            String payload = getPayloadToSign() + ":" + this.signature;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Could not generate transaction hash", e);
        }
    }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getDest() { return dest; }
    public void setDest(String dest) { this.dest = dest; }

    public long getValue() { return value; }
    public void setValue(long value) { this.value = value; }

    public String getCallData() { return callData; }
    public void setCallData(String callData) { this.callData = callData; }

    public long getGasLimit() { return gasLimit; }
    public void setGasLimit(long gasLimit) { this.gasLimit = gasLimit; }

    public long getGasPrice() { return gasPrice; }
    public void setGasPrice(long gasPrice) { this.gasPrice = gasPrice; }

    public long getNonce() { return nonce; }
    public void setNonce(long nonce) { this.nonce = nonce; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}