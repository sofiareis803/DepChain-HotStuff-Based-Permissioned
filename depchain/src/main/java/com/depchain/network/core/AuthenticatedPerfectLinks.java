package com.depchain.network.core;

import com.depchain.network.core.StubbornLinks;
import com.depchain.crypto.KeyExchange;
import com.depchain.network.envelope.Envelope;
import com.depchain.network.envelope.EnvelopeAppend;
import com.depchain.network.envelope.EnvelopeAggregatedCommitment;
import com.depchain.network.envelope.EnvelopeNonceRequest;
import com.depchain.network.envelope.EnvelopeCommitment;
import com.depchain.network.envelope.EnvelopeMessage;
import com.depchain.network.envelope.EnvelopeResponse;
import com.depchain.network.envelope.EnvelopeVote;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;


public class AuthenticatedPerfectLinks implements StubbornLinksCallback {
    private StubbornLinks sp2p;
    private Set<String> delivered = new HashSet<>();
    private AuthenticatedPerfectLinksCallback callback;
    // Maps "NodeID" -> "Shared Secret Key" derived from KeyExchange.java
    private Map<String, byte[]> connectionKeys = new HashMap<>();
    // Maps ids to UDP endpoints (IP:PORT)
    private Map<String, String> nodeEndpoints = new HashMap<>();
    private Map<String, String> endpointToNodeIds = new HashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();
    private SecureRandom secureRandom = new SecureRandom();

    public AuthenticatedPerfectLinks(StubbornLinks sp2p, AuthenticatedPerfectLinksCallback callback) {
        this.sp2p = sp2p;
        this.callback = callback;
    }

    public void alp2pSend(String dest, Envelope envelope) {
        byte[] secret = connectionKeys.get(dest);
        if (secret == null) throw new RuntimeException("No secure connection to " + dest);

        String endpoint = nodeEndpoints.get(dest);
        if (endpoint == null) throw new RuntimeException("No UDP endpoint registered for " + dest);

        envelope.setHmac(null);
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setNonce(generateNonce());

        try {
            String unsignedPayload = objectMapper.writeValueAsString(envelope);
            // 1. a = authenticate(self, dest, m);
            byte[] hmac = KeyExchange.createHmacKey(secret, unsignedPayload);
            String messageWithHmac = Base64.getEncoder().encodeToString(hmac);
            envelope.setHmac(messageWithHmac);

            // 2. trigger <sp2pSend, dest, [m, a]>;
            String payload = objectMapper.writeValueAsString(envelope); 
            
            sp2p.sp2pSend(endpoint, payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sp2pDeliver(String src, String payload) {
        String nodeId = endpointToNodeIds.get(src);
        if (nodeId == null) return; // unknown sender endpoint

        byte[] secret = connectionKeys.get(nodeId);
        if (secret == null) return; // no secure connection

        try {
            JsonNode deliveredEnv = objectMapper.readTree(payload);
            ObjectNode envelopeNode = (ObjectNode) deliveredEnv;
            JsonNode hmacNode = envelopeNode.get("hmac");
            if (hmacNode == null || hmacNode.isNull()) return;

            String receivedHmacB64 = hmacNode.asText();

            // Remove the HMAC before recomputing it
            envelopeNode.putNull("hmac");
            String unsignedPayload = objectMapper.writeValueAsString(envelopeNode);

            // 1. if verify auth(src, self, m, a) && m ∉ delivered then
            byte[] expectedHmac = KeyExchange.createHmacKey(secret, unsignedPayload);
            byte[] receivedHmac = Base64.getDecoder().decode(receivedHmacB64);

            if (MessageDigest.isEqual(receivedHmac, expectedHmac)) {
                long nonce = envelopeNode.path("nonce").asLong();
                String msgId = nonce + ":" + payload; // unique message ID based on nonce and content
                if (!delivered.contains(msgId)) {
                    // 3. delivered = delivered ∪ {m};
                    delivered.add(msgId);
                    // 2. trigger <alp2pDeliver, src, m>;
                    callback.alp2pDeliver(nodeId, deserializeEnvelope(deliveredEnv));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addConnection(String nodeId, String endpoint, byte[] sharedSecret) {
        connectionKeys.put(nodeId, sharedSecret);
        nodeEndpoints.put(nodeId, endpoint);
        endpointToNodeIds.put(endpoint, nodeId);
    }

    private long generateNonce() {
        return secureRandom.nextLong() & Long.MAX_VALUE;
    }

    private Envelope deserializeEnvelope(JsonNode deliveredEnv) throws Exception {
        if (deliveredEnv.has("transaction")) {
            return objectMapper.treeToValue(deliveredEnv, EnvelopeAppend.class);
        }

        if (deliveredEnv.has("nonce_target_client")) {
            return objectMapper.treeToValue(deliveredEnv, EnvelopeNonceRequest.class);
        }

        if (deliveredEnv.has("request_nonce")) {
            return objectMapper.treeToValue(deliveredEnv, EnvelopeResponse.class);
        }

        if (deliveredEnv.has("phase_one_vote")) {
            return objectMapper.treeToValue(deliveredEnv, EnvelopeCommitment.class);
        }

        if (deliveredEnv.has("phase_two_vote")) {
            return objectMapper.treeToValue(deliveredEnv, EnvelopeAggregatedCommitment.class);
        }

        if (deliveredEnv.has("vote")) {
            return objectMapper.treeToValue(deliveredEnv, EnvelopeVote.class);
        }

        if (deliveredEnv.has("message")) {
            return objectMapper.treeToValue(deliveredEnv, EnvelopeMessage.class);
        }

        throw new IllegalArgumentException("Unknown envelope payload");
    }
}
