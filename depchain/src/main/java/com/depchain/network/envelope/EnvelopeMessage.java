package com.depchain.network.envelope;
import com.fasterxml.jackson.annotation.JsonProperty;


public class EnvelopeMessage extends Envelope{
    @JsonProperty("message")
    private HotStuffMessageBlock hotStuffMessageBlock;

    public HotStuffMessageBlock getHotStuffMessageBlock() { return hotStuffMessageBlock; }
    public void setHotStuffMessageBlock(HotStuffMessageBlock hotStuffMessageBlock) { this.hotStuffMessageBlock = hotStuffMessageBlock; }

}