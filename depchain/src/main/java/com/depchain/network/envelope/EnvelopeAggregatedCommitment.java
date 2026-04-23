package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public class EnvelopeAggregatedCommitment extends Envelope {

    @JsonProperty("phase_two_vote")
    private AggregatedCommitmentBlock phaseTwoVote;

    public AggregatedCommitmentBlock getPhaseTwoVote() { return phaseTwoVote; }
    public void setPhaseTwoVote(AggregatedCommitmentBlock phaseTwoVote) { this.phaseTwoVote = phaseTwoVote; }
}
