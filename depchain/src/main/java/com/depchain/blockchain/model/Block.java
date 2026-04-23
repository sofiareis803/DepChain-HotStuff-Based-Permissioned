package com.depchain.blockchain.model;

import java.util.List; 
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Block {
    public static final long MAX_BLOCK_GAS = 500000;

    @JsonProperty("parent_digest")
    private String parentDigest;

    @JsonProperty("self_digest")
    private String selfDigest;

    @JsonProperty("transactions")
    private List<Transaction> transactions;
    
    @JsonProperty("world_state_hash")
    private String worldStateHash;

    @JsonProperty("total_gas_used")
    private long totalGasUsed;

    @JsonProperty("block_gas_limit")
    private long blockGasLimit;

    public Block() {
        this.blockGasLimit = MAX_BLOCK_GAS;
        this.totalGasUsed = 0;
    }

    @JsonIgnore
    public String calculateBlockDigest(Block block) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();

            // 1. Add Parent Hash
            sb.append(block.getParentDigest());
            sb.append(":");

            // 2. Add Transactions 
            if (block.getTransactions() != null) {
                for (Transaction tx : block.getTransactions()) {
                    sb.append(tx.getTransactionHash());
                }
            }

            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);

        } catch (Exception e) {
            throw new RuntimeException("Deterministic hashing failed", e);
        }
    }

    public String getParentDigest() { return parentDigest; }
    public void setParentDigest(String parentDigest) { this.parentDigest = parentDigest; }

    public String getSelfDigest() { return selfDigest; }
    public void setSelfDigest(String selfDigest) { this.selfDigest = selfDigest; }

    public List<Transaction> getTransactions() { return transactions; }
    public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }

    public String getWorldStateHash() { return worldStateHash; }
    public void setWorldStateHash(String worldStateHash) { this.worldStateHash = worldStateHash; }

    public long getTotalGasUsed() { return totalGasUsed; }
    public void setTotalGasUsed(long totalGasUsed) { this.totalGasUsed = totalGasUsed; }

    public long getBlockGasLimit() { return blockGasLimit; }
    public void setBlockGasLimit(long blockGasLimit) { this.blockGasLimit = blockGasLimit; }
}