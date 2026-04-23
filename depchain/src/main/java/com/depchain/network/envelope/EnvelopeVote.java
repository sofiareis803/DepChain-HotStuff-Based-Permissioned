package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EnvelopeVote extends Envelope {
    @JsonProperty("vote")
    private VoteBlock vote;

    public VoteBlock getVote() { return vote; }
    public void setVote(VoteBlock vote) { this.vote = vote; }
}
