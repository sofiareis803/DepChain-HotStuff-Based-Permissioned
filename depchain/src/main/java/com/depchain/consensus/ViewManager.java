package com.depchain.consensus;

public class ViewManager {

    public long getLeaderForView(long viewNumber, int totalReplicas) {
        return viewNumber % totalReplicas;
    }

    public boolean isLeader(int replicaId, long viewNumber) {
        return replicaId == viewNumber;
    }

    public int getLeaderIdforView(long viewId, int totalReplicas) {
        return (int) (viewId % totalReplicas);
    }
}