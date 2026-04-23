package com.depchain.network;

import com.depchain.crypto.KeyStoreController;
import com.depchain.network.core.AuthenticatedPerfectLinksCallback;
import com.depchain.network.envelope.EnvelopeMessage;
import com.depchain.network.envelope.HotStuffMessageBlock;
import com.depchain.network.envelope.Phase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

public class APLChannelTestRun {

    public static void main(String[] args) throws Exception {
        System.out.println("Authenticated Perfect Links Test");

        try {
            // Setup keys 
            String keystoreNode1 = "../security/keystores/node1.p12";
            String keystoreNode2 = "../security/keystores/node2.p12";
            String truststore = "../security/truststores/truststore.p12";
            String password = "depchain";

            KeyStoreController kscNode1 = new KeyStoreController(keystoreNode1, password);
            KeyStoreController kscNode2 = new KeyStoreController(keystoreNode2, password);
            KeyStoreController kscTrust = new KeyStoreController(truststore, password);

            // Wait for the message to be received
            CountDownLatch messageReceivedLatch = new CountDownLatch(1);
            ObjectMapper mapper = new ObjectMapper();

            // Create Callback for Node 1
            AuthenticatedPerfectLinksCallback callbackNode1 = (src, envelopeReceived) -> {
                System.out.println("[Node1] Received unexpectedly: " + envelopeReceived + " from " + src);
            };

            // Create Callback for Node 2
            AuthenticatedPerfectLinksCallback callbackNode2 = (src, envelopeReceived) -> {
                System.out.println("[Node2] Authenticated message successfully received!");
                System.out.println("[Node2] Src: " + src);
                try {
                    String envelopeJson = mapper.writeValueAsString(envelopeReceived);
                    System.out.println("[Node2] Envelope: " + envelopeJson);
                } catch (JsonProcessingException e) {
                    System.err.println("[Node2] Failed to serialize envelope: " + e.getMessage());
                }
                messageReceivedLatch.countDown(); // signal success
            };

            System.out.println("Initializing Channels...");
            // Initialize Channels
            // Node 1 listens on 8081
            APLChannel channelNode1 = new APLChannel(8081, "node1", kscNode1, kscTrust, callbackNode1);
            
            // Node 2 listens on 8082
            APLChannel channelNode2 = new APLChannel(8082, "node2", kscNode2, kscTrust, callbackNode2);

            System.out.println("Establishing Secure Connections via KeyExchange...");
            // Node 1 connects to Node 2
            channelNode1.addConnection("node2", "127.0.0.1:8082");
            // Node 2 connects back to Node 1
            channelNode2.addConnection("node1", "127.0.0.1:8081");

            System.out.println("Sending test message from Node1 -> Node2...");
            EnvelopeMessage envelope = new EnvelopeMessage();
            HotStuffMessageBlock msg = new HotStuffMessageBlock();
            msg.setMessageType(Phase.PREPARE);
            msg.setViewNumber(1L);
            envelope.setHotStuffMessageBlock(msg);

            channelNode1.send("node2", envelope);

            // Wait 5 seconds for message 
            boolean success = messageReceivedLatch.await(5, TimeUnit.SECONDS);

            if (success) {
                System.out.println("TEST PASSED: Message was received, HMAC verified, and delivered.");
            } else {
                System.err.println("TEST FAILED: Timeout waiting for message.");
            }

            // Cleanup
            channelNode1.shutdown();
            channelNode2.shutdown();
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
