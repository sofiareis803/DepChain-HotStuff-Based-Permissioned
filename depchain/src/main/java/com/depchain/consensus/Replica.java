package com.depchain.consensus;

import com.depchain.crypto.CryptoUtils;
import com.depchain.crypto.KeyStoreController;
import com.depchain.blockchain.model.Transaction;
import com.depchain.state.EvmExecutionService;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.depchain.ConfigReader;
import com.depchain.GenesisReader;
import com.depchain.blockchain.BlockTree;
import com.depchain.blockchain.model.Block;
import com.depchain.network.Network;
import com.depchain.network.envelope.Envelope;
import com.depchain.network.envelope.EnvelopeMessage;
import com.depchain.network.envelope.HotStuffMessageBlock;
import com.depchain.network.envelope.Phase;

public class Replica {
    // Used by replica
    private int replicaId;
    private int totalReplicas;
    private long viewNumber;
    private long attemptedViewNumber;
    private BlockTree blockTree;
    private ViewManager viewManager;
    private CryptoUtils cryptoUtils;
    private Network network;
    private MessageHandler handler;
    private List<String> commitedTransactions;
    private KeyStoreController truststore;
    private FaultInjector faultInjector;
    private boolean byzantineMode;
    private EvmExecutionService evmExecutionService;

    // Timer stuff
    private Timeout timeout;

    // Used by the leader
    private List<Transaction> pendingTransactions;
    private Set<String> decidedViews;
    private Map<String, ClientRequestRef> pendingClientRequests = new HashMap<>();
    private Set<String> executedRequests = new HashSet<>();
    private long syntheticNonce = 0;

    public static class ClientRequestRef {
        public long clientId;
        public long requestNonce;

        public ClientRequestRef(long clientId, long requestNonce) {
            this.clientId = clientId;
            this.requestNonce = requestNonce;
        }
    }


    public Replica(int id, int totalReplicas, Network network, KeyStoreController truststore, ByzantineConfig byzantineConfig) {
        this.replicaId = id;

        Block genesisBlock = GenesisReader.readGenesisBlock();
        this.blockTree = new BlockTree(genesisBlock);

        this.viewManager = new ViewManager();
        this.cryptoUtils = new CryptoUtils(id);
        this.network = network;
        this.truststore = truststore;
        this.viewNumber = 0;
        this.attemptedViewNumber = 0;
        this.totalReplicas = totalReplicas;
        this.commitedTransactions = new ArrayList<>();
        this.timeout = new Timeout();
        this.decidedViews = new HashSet<>();
        this.pendingTransactions = new ArrayList<>();
        this.pendingClientRequests = new HashMap<>();
        this.faultInjector = new FaultInjector(byzantineConfig, this);
        this.handler = new MessageHandler(this, faultInjector);
        this.byzantineMode = false;

        this.evmExecutionService = new EvmExecutionService();
        new GenesisReader().initializeWorld(this.evmExecutionService);
        this.handler = new MessageHandler(this);
        
        System.out.println("Replica " + replicaId + " initialized with totalReplicas=" + this.totalReplicas
                + ", viewNumber=" + this.viewNumber + ", attemptedViewNumber=" + this.attemptedViewNumber);
        
        if (byzantineConfig.isEnabled()) {
            System.out.println("[BYZANTINE] Replica " + replicaId + " configured with: " + byzantineConfig);
        }
    }

    public static void main(String[] args) throws Exception {

        int replicaId = Integer.parseInt(args[0]);
        int configNodeId = replicaId + 1;
        
        ByzantineConfig byzantineConfig = new ByzantineConfig();
        boolean byzantineMode = false;
        
        for (int i = 1; i < args.length; i++) {
            byzantineMode = true;
            String arg = args[i].toLowerCase();
            
            if (arg.startsWith("--byzantine-type=")) {
                String type = arg.substring("--byzantine-type=".length());
                byzantineConfig = new ByzantineConfig(ByzantineConfig.ByzantineEnumType.fromString(type));
            } else if (arg.startsWith("--affected-phases=")) {
                String phase = arg.substring("--affected-phases=".length());
                byzantineConfig.setPhase(phase);
            }
        }
  
        ConfigReader configReader = new ConfigReader();
        Map<Integer, InetSocketAddress> nodeAddresses = configReader.getReplicaAddresses();

        InetSocketAddress self = nodeAddresses.get(configNodeId);
        if (self == null) {
            throw new IllegalArgumentException("Replica id " + replicaId + " not found in config.txt");
        }

        String nodeId = "node" + configNodeId;

        KeyStoreController keystore = new KeyStoreController("../security/keystores/" + nodeId + ".p12", "depchain");
        KeyStoreController truststore = new KeyStoreController("../security/truststores/truststore.p12", "depchain");

        Network network = new Network(self.getPort(), nodeId, keystore, truststore);

        for (Map.Entry<Integer, InetSocketAddress> entry : nodeAddresses.entrySet()) {
            int peerNodeId = entry.getKey();
            InetSocketAddress peerAddr = entry.getValue();

            if (peerNodeId == configNodeId)
                continue;

            network.addConnection(
                "node" + peerNodeId,
                peerAddr.getHostString() + ":" + peerAddr.getPort()
            );
        }

        // Create connections to clients
       setupClientsConnections(network, configReader);

        Replica replica = new Replica(replicaId, nodeAddresses.size(), network, truststore, byzantineConfig);
        replica.setByzantineMode(byzantineMode);
        replica.run();
    }

    public void run() {
        System.out.println("Replica " + replicaId + " entering run loop");
        startViewTimer();
        while (true) {
            try {
                Envelope env = network.receive();
                System.out
                        .println("Replica " + replicaId + " received envelope type=" + env.getClass().getSimpleName());
                startViewTimer();
                handler.handle(env, this.byzantineMode);
            } catch (InterruptedException e) {
                System.out.println("Replica " + replicaId + " interrupted while waiting for network.receive(); exiting run loop");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void nextView() {
        System.out.println("Replica " + replicaId + " entering nextView with viewNumber=" + viewNumber
                + " attemptedViewNumber=" + attemptedViewNumber + " timeout=" + timeout);
        this.attemptedViewNumber++;
        if (attemptedViewNumber > viewNumber + 1) {
            System.out.println(
                    "Replica " + replicaId + " increased timeout to " + timeout + " due to repeated view changes");
        }

        int newLeader = this.viewManager.getLeaderIdforView(attemptedViewNumber, totalReplicas);
        System.out.println("Replica " + replicaId + " sending NEW_VIEW for attemptedView=" + attemptedViewNumber
                + " to leader=" + newLeader);
        HotStuffMessageBlock block = new HotStuffMessageBlock();
        block.setMessageType(Phase.NEW_VIEW);
        block.setViewNumber(attemptedViewNumber);
        block.setQc(blockTree.getLockedQc());

        if (newLeader == this.replicaId) {
            System.out.println("Replica " + replicaId + " is leader for attemptedView=" + attemptedViewNumber
                    + ", handling NEW_VIEW locally");
            handler.handleNewView(block);
            startViewTimer();
            return;
        }

        EnvelopeMessage env = new EnvelopeMessage();
        env.setSenderId(this.replicaId);
        env.setHotStuffMessageBlock(block);
        network.send(env, newLeader);

        System.out.println("Replica " + replicaId + " NEW_VIEW sent to leader=" + newLeader);
        startViewTimer();
    }

    public void triggerNextView() {
        nextView();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // HELPER functions for tests
    public void addPendingTransaction(Transaction tx) {
        this.pendingTransactions.add(tx);
        System.out.println("Replica " + replicaId + " added pending transaction=" + tx + " pendingCount="
                + this.pendingTransactions.size());
    }

    public synchronized void registerClientRequest(Transaction tx, long clientId) {
        // Use hash to ensure uniqueness, the nonce is not enough if the same client sends multiple requests with the same nonce
        String key = tx.getTransactionHash();

        // Replay Check: if the request is already executed or pending, ignore it 
        if (executedRequests.contains(key) || pendingClientRequests.containsKey(key)) {
            System.out.println("Duplicate transaction detected and ignored: " + key);
            return;
        }
        pendingClientRequests.put(key, new ClientRequestRef(clientId, tx.getNonce()));
        pendingTransactions.add(tx);
        System.out.println("Transaction registered");
    }

    public synchronized ClientRequestRef popClientRequest(String requestKey) {
        return pendingClientRequests.remove(requestKey);
    }

    public synchronized boolean markExecutedRequest(String requestKey) {
        return executedRequests.add(requestKey);
    }

    public synchronized void removePendingTransaction(String requestKey) {
        pendingTransactions.removeIf(tx -> requestKey.equals(tx.getTransactionHash()));
    }

    public List<String> getCommittedTransactions() {
        System.out
                .println("Replica " + replicaId + " returning committed transactions size=" + this.commitedTransactions.size());
        return this.commitedTransactions;
    }

    public boolean verifyTransactionSignature(String clientId, Transaction transaction) {
        try {
            String message = transaction.getPayloadToSign();
            return truststore.verifySignature(clientId, message, transaction.getSignature());
        } catch (Exception e) {
            System.err.println("Failed to verify client signature: " + e.getMessage());
            return false;
        }
    }

    private static void setupClientsConnections(Network network, ConfigReader configClients) {
        // Get the clients adresses from the config and connect to each client 
        Map<Integer, InetSocketAddress> nodeAddresses = configClients.getClientAddresses();
        
        for (Map.Entry<Integer, InetSocketAddress> entry : nodeAddresses.entrySet()) {
            String nodeId = "client" + entry.getKey();
            InetSocketAddress address = entry.getValue();
            String endpoint = address.getHostString() + ":" + address.getPort();
            
            System.out.println("Adding connection to " + nodeId + " at " + endpoint);
            network.addConnection(nodeId, endpoint);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Helper functions

    public int getReplicaId() {
        return this.replicaId;
    }

    public int getTotalReplicas() {
        return this.totalReplicas;
    }

    public long getViewNumber() {
        return this.viewNumber;
    }

    public long getAttemptedViewNumber() {
        return this.attemptedViewNumber;
    }

    public void setAttemptedViewNumber(long view) {
        this.attemptedViewNumber = view;
    }

    public void setViewNumber(Long viewNumber) {
        this.viewNumber = viewNumber;
    }
    
    public Timeout getViewTimer() {
        return this.timeout;
    }

    public void startViewTimer() {
        this.timeout.startTime(this::nextView);
    }

    public FaultInjector getFaultInjector() {
        return this.faultInjector;
    }

    public BlockTree getBlockTree() {
        return this.blockTree;
    }

    public ViewManager getViewManager() {
        return this.viewManager;
    }

    public CryptoUtils getCryptoUtils() {
        return this.cryptoUtils;
    }

    public Network getNetwork() {
        return this.network;
    }

    public Set<String> getDecidedViews() {
        return this.decidedViews;
    }

    public List<Transaction> getPendingTransactions() {
        return this.pendingTransactions;
    }

    public void commitTransaction(String tx){
        this.commitedTransactions.add(tx);
    }

    public EvmExecutionService getEvmExecutionService() {
        return this.evmExecutionService;
    }

    public void setByzantineMode(boolean mode) {
        this.byzantineMode = mode;
    }
}