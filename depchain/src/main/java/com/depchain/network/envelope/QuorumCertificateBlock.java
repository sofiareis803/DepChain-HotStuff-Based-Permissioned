package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.depchain.blockchain.model.Block;

public class QuorumCertificateBlock {
    @JsonProperty("qc_type")
    private String qcType;

    @JsonProperty("view_number")
    private long viewNumber;

    @JsonProperty("block")
    private Block block;

    @JsonProperty("aggregate_signature")
    private String aggregateSignature;

    public String getQcType() { return qcType; }
    public void setQcType(String qcType) { this.qcType = qcType; }

    public String getAggregateSignature() { return aggregateSignature; }
    public void setAggregateSignature(String aggregateSignature) { this.aggregateSignature = aggregateSignature; }
   
    public long getViewNumber() { return viewNumber; }
    public void setViewNumber(long viewNumber) { this.viewNumber = viewNumber; }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }
    
}
