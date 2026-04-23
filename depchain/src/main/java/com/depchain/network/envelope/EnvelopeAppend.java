package com.depchain.network.envelope;

import com.depchain.blockchain.model.Transaction;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EnvelopeAppend extends Envelope {
    @JsonProperty("transaction")
    private Transaction transaction;

    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }
}