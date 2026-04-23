package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EnvelopeNonceRequest extends Envelope {
    @JsonProperty("nonce_target_client")
    private long nonceTargetClient;

    public long getNonceTargetClient() { return nonceTargetClient; }
    public void setNonceTargetClient(long nonceTargetClient) { this.nonceTargetClient = nonceTargetClient; }
}
