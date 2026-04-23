package com.depchain.network;

import java.security.PublicKey;
import java.security.PrivateKey;

import com.depchain.crypto.KeyStoreController;
import com.depchain.network.core.AuthenticatedPerfectLinks;
import com.depchain.network.core.AuthenticatedPerfectLinksCallback;
import com.depchain.network.core.FairLossLinks;
import com.depchain.network.core.StubbornLinks;
import com.depchain.crypto.KeyExchange;
import com.depchain.network.envelope.Envelope;


public class APLChannel {
    private FairLossLinks flp2p;
    private StubbornLinks sp2p;
    private AuthenticatedPerfectLinks alp2p;

    private final String id;
    private KeyStoreController keyStore;
    private KeyStoreController truststore;

    public APLChannel(int port, String id, KeyStoreController keyStore, KeyStoreController truststore, AuthenticatedPerfectLinksCallback callback) {
        this.id = id;
        this.keyStore = keyStore;
        this.truststore = truststore;
        
        // 1-Initialize the Fair Loss Links Layer
        this.flp2p = new FairLossLinks(port, (src, m) -> {
            if (this.sp2p != null) {
                this.sp2p.flp2pDeliver(src, m);
            }
        });

        // 2-Initialize the Stubborn Links Layer
        this.sp2p = new StubbornLinks(this.flp2p, (src, m) -> {
            if (this.alp2p != null) {
                this.alp2p.sp2pDeliver(src, m);
            }
        });

        // 3-Initialize the Authenticated Perfect Links Layer
        this.alp2p = new AuthenticatedPerfectLinks(this.sp2p, callback);
    }

    /**
     * Adds a connection to another node by performing key agreement and storing the shared secret.
     * @param nodeId Logical ID of the other node (e.g. "node2")
     * @param endpoint IP:PORT of the other node
     */
    public void addConnection(String nodeId, String endpoint) {
        try {
            PrivateKey privateKey = keyStore.getPrivateKey(id);
            PublicKey publicKey = truststore.getPublicKey(nodeId);

            // 1. Perform key agreement to derive a shared secret
            byte[] sharedSecret = KeyExchange.sharedSecret(privateKey, publicKey);

            // 2. Store the shared secret and endpoint for this connection
            alp2p.addConnection(nodeId, endpoint, sharedSecret);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(String dest, Envelope envelope) {
        alp2p.alp2pSend(dest, envelope);
    }

    public void shutdown() {
        sp2p.shutdown();
        flp2p.shutdown();
    }

}
