package com.depchain.network.envelope;

import com.depchain.blockchain.model.Transaction;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeBlock {
    @JsonProperty("parent_digest")
    private String parentDigest;

    @JsonProperty("transaction")
    private List<Transaction> transaction;

    @JsonProperty("self_digest")
    private String selfDigest;

    public String getParentDigest() { return parentDigest; }
    public void setParentDigest(String parentDigest) { this.parentDigest = parentDigest; }

    public List<Transaction> getTransaction() { return transaction; }
    public void setTransaction(List<Transaction> transaction) { this.transaction = transaction; }

    public String getSelfDigest() { return selfDigest; }
    public void setSelfDigest(String selfDigest) { this.selfDigest = selfDigest; }
}
