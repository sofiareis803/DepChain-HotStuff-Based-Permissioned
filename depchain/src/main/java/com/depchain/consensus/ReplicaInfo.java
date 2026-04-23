package com.depchain.consensus;

public class ReplicaInfo {
    public int id;
    public String ip;
    public int port;

    public ReplicaInfo(int id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }
}
