package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CommitementBlock {
    @JsonProperty("qc_type")
    private Phase qcType;

    @JsonProperty("view_number")
    private long viewNumber;

    @JsonProperty("node_digest")
    private String nodeDigest;

    @JsonProperty("nonce_commitment")
    private String nonceCommitment;

    public Phase getQcType() { return qcType; }
    public void setQcType(Phase qcType) { this.qcType = qcType; }

    public long getViewNumber() { return viewNumber; }
    public void setViewNumber(long viewNumber) { this.viewNumber = viewNumber; }

    public String getNodeDigest() { return nodeDigest; }
    public void setNodeDigest(String nodeDigest) { this.nodeDigest = nodeDigest; }

    public String getNonceCommitment() { return nonceCommitment; }
    public void setNonceCommitment(String nonceCommitment) { this.nonceCommitment = nonceCommitment; }
}
