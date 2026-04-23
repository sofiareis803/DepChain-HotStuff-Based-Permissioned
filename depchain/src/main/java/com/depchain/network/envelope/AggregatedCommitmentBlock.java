package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public class AggregatedCommitmentBlock {
    @JsonProperty("qc_type")
    private Phase qcType;

    @JsonProperty("view_number")
    private long viewNumber;

    @JsonProperty("node_digest")
    private String nodeDigest;

    @JsonProperty("aggregated_commitment")
    private String aggregatedCommitment;

    @JsonProperty("signer_indices")
    private Set<Long> signerIndices;

    public Phase getQcType() { return qcType; }
    public void setQcType(Phase qcType) { this.qcType = qcType; }

    public long getViewNumber() { return viewNumber; }
    public void setViewNumber(long viewNumber) { this.viewNumber = viewNumber; }

    public String getNodeDigest() { return nodeDigest; }
    public void setNodeDigest(String nodeDigest) { this.nodeDigest = nodeDigest; }

    public String getAggregatedCommitment() { return aggregatedCommitment; }
    public void setAggregatedCommitment(String aggregatedCommitment) { this.aggregatedCommitment = aggregatedCommitment; }

    public Set<Long> getSignerIndices() { return signerIndices; }
    public void setSignerIndices(Set<Long> signerIndices) { this.signerIndices = signerIndices; }
}
