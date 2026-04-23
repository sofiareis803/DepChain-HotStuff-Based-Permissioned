package com.depchain.network;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.depchain.crypto.KeyStoreController;
import com.depchain.network.core.AuthenticatedPerfectLinksCallback;
import com.depchain.network.envelope.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Network {
    private final APLChannel channel;
    private final BlockingQueue<Envelope> incoming = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private final List<String> connectedNodes = new ArrayList<>();

    public Network(int localPort, String nodeId, KeyStoreController keystore, KeyStoreController truststore) {
        AuthenticatedPerfectLinksCallback callback = (src, envelope) -> {
            System.out.println("[Node " + nodeId + "] Received envelope from " + src);
            try {
                incoming.put(envelope);
            } catch (InterruptedException e) {
                System.out.println("Error putting envelope into incoming queue");
                e.printStackTrace();
            }
        };
        this.channel = new APLChannel(localPort, nodeId, keystore, truststore, callback);

    }

    public void addConnection(String peerId, String ipPort) {
        channel.addConnection(peerId, ipPort);
        connectedNodes.add(peerId);
    };

    public void send(Envelope env, long destReplicaId) {
        String dest = "node" + (destReplicaId + 1);
        channel.send(dest, env);

    };

    public void sendToPeer(Envelope env, String peerId) {
        channel.send(peerId, env);
    }

    public void broadcast(Envelope env) {
        for (String nodeId : connectedNodes) {
            channel.send(nodeId, env);
        }
    };

    public Envelope receive() throws InterruptedException {
        return incoming.take();
    };

    public void shutdown() {
        channel.shutdown();
    }
}