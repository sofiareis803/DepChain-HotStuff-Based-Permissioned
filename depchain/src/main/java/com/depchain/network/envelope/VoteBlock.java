package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public class VoteBlock {
 

    @JsonProperty("qc_type")
    private Phase qcType;

    @JsonProperty("view_number")
    private long viewNumber;

    @JsonProperty("node_digest")
    private String nodeDigest;

    @JsonProperty("partial_signature")
    private String partialSignature;

    public Phase getQcType() {  return qcType; }
    public void setQcType(Phase qcType) { this.qcType = qcType; }
    
    public long getViewNumber() { return viewNumber; }
    public void setViewNumber(long viewNumber) { this.viewNumber = viewNumber; }

    public String getNodeDigest() { return nodeDigest; }
    public void setNodeDigest(String nodeDigest) { this.nodeDigest = nodeDigest; }

    public String getPartialSignature() { return partialSignature; }
    public void setPartialSignature(String partialSignature) { this.partialSignature = partialSignature; }

}