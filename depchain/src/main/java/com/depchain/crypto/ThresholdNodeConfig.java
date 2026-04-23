package com.depchain.crypto;

public class ThresholdNodeConfig {
    private int replicaIndex;
    private int threshold;
    private int totalReplicas;
    private String aggregatePublicKey; // The master public key (every key together)
    private String privateShare; // Part of the private key share for this node

    public int getReplicaIndex() { return replicaIndex; }
    public void setReplicaIndex(int replicaIndex) { this.replicaIndex = replicaIndex; }

    public int getThreshold() { return threshold; }
    public void setThreshold(int threshold) { this.threshold = threshold; }

    public int getTotalReplicas() { return totalReplicas; }
    public void setTotalReplicas(int totalReplicas) { this.totalReplicas = totalReplicas; }

    public String getAggregatePublicKey() { return aggregatePublicKey; }
    public void setAggregatePublicKey(String aggregatePublicKey) { this.aggregatePublicKey = aggregatePublicKey; }

    public String getPrivateShare() { return privateShare; }
    public void setPrivateShare(String privateShare) { this.privateShare = privateShare; }
}