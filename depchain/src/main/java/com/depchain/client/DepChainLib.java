package com.depchain.client;

import com.depchain.ConfigReader;
import com.depchain.network.APLChannel;
import com.depchain.network.envelope.EnvelopeAppend;
import com.depchain.network.envelope.EnvelopeResponse;
import com.depchain.network.envelope.Envelope;
import com.depchain.network.envelope.EnvelopeNonceRequest;
import com.depchain.blockchain.model.Transaction;
import com.depchain.crypto.KeyStoreController;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Set;


public class DepChainLib {
    private static final String TOKEN_CONTRACT_ADDRESS = "0x0c5407c61e8f39fed0f055773b7bba130405309f";
    private static final String SELECTOR_TRANSFER = "a9059cbb";
    private static final String SELECTOR_TRANSFER_FROM = "23b872dd";
    private static final String SELECTOR_APPROVE = "095ea7b3";
    private static final String SELECTOR_INCREASE_ALLOWANCE = "39509351";
    private static final String SELECTOR_DECREASE_ALLOWANCE = "a457c2d7";
    
    private int clientId;
    private int sequenceNumber;
    private ConfigReader configNodes;
    private APLChannel alp2p;
    KeyStoreController keyStore;


    private Map<Long, RequestTracker> activeRequests = new ConcurrentHashMap<>();

    public DepChainLib(int id, ConfigReader reader) {
        this.clientId = id;
        this.configNodes = reader;
        this.sequenceNumber = 0;

        keyStore = new KeyStoreController("../security/keystores/client"+id+".p12", "depchain");
        KeyStoreController truststore = new KeyStoreController("../security/truststores/truststore.p12", "depchain");

        this.alp2p = new APLChannel(
            12000 + id, 
            "client"+id,
            keyStore,
            truststore,
            (src, m) -> {
                handleNodeResponse(src, m);
            }
        );
        setupNodeConnections();

        try {
            EnvelopeNonceRequest req = new EnvelopeNonceRequest();
            req.setSenderId(this.clientId);
            req.setNonce(-1); 
            req.setNonceTargetClient(this.clientId);

            RequestTracker tracker = new RequestTracker(configNodes.getReplicaAddresses().size());
            activeRequests.put(-1L, tracker);

            for (Integer nodeId : configNodes.getReplicaAddresses().keySet()) {
                String nodeIdStr = "node" + nodeId;
                alp2p.send(nodeIdStr, req);
            }

            tracker.waitForMajority(10000); // 10s wait for network
            activeRequests.remove(-1L);
            System.out.println("Nonce initialized to: " + this.sequenceNumber);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void setupNodeConnections() {
        // Get the node adresses from the config and connect to each node 
        // Sending the message to all the consensus nodes
        Map<Integer, InetSocketAddress> nodeAddresses = configNodes.getReplicaAddresses();
        
        for (Map.Entry<Integer, InetSocketAddress> entry : nodeAddresses.entrySet()) {
            String nodeId = "node" + entry.getKey();
            InetSocketAddress address = entry.getValue();
            String endpoint = address.getHostString() + ":" + address.getPort();
            
            System.out.println("Adding connection to " + nodeId + " at " + endpoint);
            alp2p.addConnection(nodeId, endpoint);
        }
    }
    private void handleNodeResponse(String src, Envelope envelope) {
        try {
            if (envelope instanceof EnvelopeResponse response) {
                long requestNonce = response.getRequestNonce();
                if (requestNonce == -1 && response.getMessage().startsWith("NONCE:")) {
                    long nonce = Long.parseLong(response.getMessage().split(":")[1]);
                    if (nonce > this.sequenceNumber) {
                        this.sequenceNumber = (int) nonce;
                    }
                    boolean success = true;
                    RequestTracker tracker = activeRequests.get(-1L);
                    if (tracker != null) {
                        tracker.addResponse(src, success);
                    }
                    return;
                }
                RequestTracker tracker = activeRequests.get(requestNonce);

                if (tracker != null) {
                    boolean success = "ACK".equals(response.getMessage());
                    RequestTracker.ResponseOutcome outcome = tracker.addResponse(src, success);
                    if (outcome.accepted && success) {
                        System.out.println("[Client" + clientId + "] ACK received from " + src + " for request " + requestNonce);
                    }
                    else if (outcome.accepted && !success) {
                        System.out.println("[Client" + clientId + "] NACK from " + src + ": " + response.getMessage());
                    }
                    if (outcome.quorumReached) {
                        System.out.println("[Client" + clientId + "] Quorum reached for request " + requestNonce);
                    }
                    if (outcome.discardNotice) {
                        System.out.println("[Client" + clientId + "] Discarding further responses for request " + requestNonce + " (quorum already reached)");
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to process response: " + e.getMessage());
        }
    }   

    public boolean transfer(int toClientId, long amount, long gasPrice, long gasLimit) {
        return submitTokenCall(encodeCall(SELECTOR_TRANSFER, encodeClientAddress(toClientId), amount), gasPrice, gasLimit);
    }

    public boolean transferFrom(int fromClientId, int toClientId, long amount, long gasPrice, long gasLimit) {
        return submitTokenCall(
            encodeCall(SELECTOR_TRANSFER_FROM, encodeClientAddress(fromClientId), encodeClientAddress(toClientId), amount),
            gasPrice,
            gasLimit
        );
    }

    public boolean approve(int spenderClientId, long amount, long gasPrice, long gasLimit) {
        return submitTokenCall(encodeCall(SELECTOR_APPROVE, encodeClientAddress(spenderClientId), amount), gasPrice, gasLimit);
    }

    public boolean increaseAllowance(int spenderClientId, long amount, long gasPrice, long gasLimit) {
        return submitTokenCall(
            encodeCall(SELECTOR_INCREASE_ALLOWANCE, encodeClientAddress(spenderClientId), amount), 
            gasPrice, gasLimit
        );
    }

    public boolean decreaseAllowance(int spenderClientId, long amount, long gasPrice, long gasLimit) {
        return submitTokenCall(
            encodeCall(SELECTOR_DECREASE_ALLOWANCE, encodeClientAddress(spenderClientId), amount), 
            gasPrice, gasLimit
        );
    }

    private boolean submitTokenCall(String callData, long gasPrice, long gasLimit) {
        long requestNonce = this.sequenceNumber;
        int totalNodes = configNodes.getReplicaAddresses().size();    

        try {
            RequestTracker tracker = new RequestTracker(totalNodes);
            activeRequests.put(requestNonce, tracker);

            Transaction tx = new Transaction();
            tx.setSender("client"+clientId);
            tx.setDest(TOKEN_CONTRACT_ADDRESS);
            tx.setValue(0L);
            tx.setCallData(callData);
            tx.setNonce(requestNonce);
            tx.setGasLimit(gasLimit);
            tx.setGasPrice(gasPrice);
            
            // Sign the transaction
            try {
                String payload = tx.getPayloadToSign();
                String signature = keyStore.sign("client"+clientId, payload);
                tx.setSignature(signature);
            } catch (Exception e) {
                System.err.println("[Client" + clientId + "] Failed to sign transaction: " + e.getMessage());
                return false;
            }

            EnvelopeAppend transfer = new EnvelopeAppend();
            transfer.setTransaction(tx);
            transfer.setSenderId(clientId);

            Map<Integer, InetSocketAddress> nodeAddresses = configNodes.getReplicaAddresses();
            for (Integer nodeId : nodeAddresses.keySet()) {
                String nodeIdStr = "node" + nodeId;
                alp2p.send(nodeIdStr, transfer);
            }

            boolean result = tracker.waitForMajority(80000);
            activeRequests.remove(requestNonce);
            this.sequenceNumber++;
            
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String encodeCall(String selector, Object... args) {
        StringBuilder encoded = new StringBuilder("0x").append(selector);
        for (Object arg : args) {
            encoded.append(encodeArgument(arg));
        }
        return encoded.toString();
    }

    private String encodeArgument(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }

        if (value instanceof Number number) {
            return String.format("%064x", number.longValue());
        }

        String text = value.toString();
        String normalized = text.startsWith("0x") ? text.substring(2) : text;
        return String.format("%064x", new java.math.BigInteger(normalized, 16));
    }

    private String encodeClientAddress(int clientId) {
        return deriveAddressFromAlias("client" + clientId);
    }

    private String deriveAddressFromAlias(String alias) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(alias.getBytes(StandardCharsets.UTF_8));

            byte[] addressBytes = new byte[20];
            System.arraycopy(hash, hash.length - 20, addressBytes, 0, 20);

            StringBuilder sb = new StringBuilder("0x");
            for (byte b : addressBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to derive address from alias: " + alias, e);
        }
    }


    private static class RequestTracker {
        private static class ResponseOutcome {
            boolean accepted;
            boolean quorumReached;
            boolean discardNotice;
        }

        private CountDownLatch latch = new CountDownLatch(1);
        private final int majorityNeeded;
        private final Set<String> respondedNodes = ConcurrentHashMap.newKeySet();
        private int successCount;
        private int failCount;
        private boolean result = false;
        private boolean discardNoticePrinted = false;

        public RequestTracker(int totalNodes) {
            this.majorityNeeded = (totalNodes / 2) + 1;
            this.failCount = 0;
            this.successCount = 0;

        }

        public synchronized ResponseOutcome addResponse(String nodeId, boolean success) {
            ResponseOutcome outcome = new ResponseOutcome();

            // Quorum already reached, discard
            if (latch.getCount() == 0) {
                if (!discardNoticePrinted) {
                    discardNoticePrinted = true;
                    outcome.discardNotice = true;
                }
                return outcome;
            }

            if (!respondedNodes.add(nodeId)) {
                return outcome;
            }

            outcome.accepted = true;
            if (success) {
                successCount++;
                if (successCount >= majorityNeeded) {
                    result = true;
                    latch.countDown();
                    outcome.quorumReached = true;
                }
            } else {
                failCount++;
                if (failCount >= majorityNeeded) {
                    result = false;
                    latch.countDown();
                    outcome.quorumReached = true;
                }
            }
            
            return outcome;
        }

        public boolean waitForMajority(long timeoutMs) {
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
                return result;
            } catch (InterruptedException e) {
                return false;
            }
        }

    }
}