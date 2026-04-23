package com.depchain.network.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.depchain.blockchain.model.Block;

public class HotStuffMessageBlock {

    @JsonProperty("message_type")
    private Phase messageType;

    @JsonProperty("view_number")
    private long viewNumber;

    @JsonProperty("block")
    private Block block;

    @JsonProperty("justify")
    private QuorumCertificateBlock qc;

    public Phase getMessageType() { return messageType; }
    public void setMessageType(Phase newView) { this.messageType = newView; }

    public long getViewNumber() {  return viewNumber; }
    public void setViewNumber(long viewNumber) { this.viewNumber = viewNumber; }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }

    public QuorumCertificateBlock getQc() { return qc; }
    public void setQc(QuorumCertificateBlock qc) { this.qc = qc; }
}