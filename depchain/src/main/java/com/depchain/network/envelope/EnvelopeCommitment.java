package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EnvelopeCommitment extends Envelope {
    @JsonProperty("phase_one_vote")
    private CommitementBlock phaseOneVote;

    public CommitementBlock getPhaseOneVote() { return phaseOneVote; }
    public void setPhaseOneVote(CommitementBlock phaseOneVote) { this.phaseOneVote = phaseOneVote; }
}