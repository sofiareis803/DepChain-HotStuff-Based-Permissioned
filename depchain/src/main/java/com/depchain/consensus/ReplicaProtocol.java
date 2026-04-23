package com.depchain.consensus;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.depchain.blockchain.BlockTree;
import com.depchain.blockchain.model.Transaction;
import com.depchain.blockchain.model.Block;
import com.depchain.crypto.CryptoUtils;
import com.depchain.network.Network;
import com.depchain.network.envelope.AggregatedCommitmentBlock;
import com.depchain.network.envelope.CommitementBlock;
import com.depchain.network.envelope.EnvelopeCommitment;
import com.depchain.network.envelope.EnvelopeResponse;
import com.depchain.network.envelope.EnvelopeVote;
import com.depchain.network.envelope.HotStuffMessageBlock;
import com.depchain.network.envelope.Phase;
import com.depchain.network.envelope.QuorumCertificateBlock;
import com.depchain.network.envelope.VoteBlock;
import com.depchain.state.EvmExecutionService;
import com.depchain.state.EvmExecutionService.BlockExecutionResult;
public class ReplicaProtocol {

    private Replica replica;
    private BlockTree blockTree;
    private ViewManager viewManager;
    private CryptoUtils cryptoUtils;
    private Network network;
    private Set<String> pendingVotes;
    private Set<String> sentVotes;

    public ReplicaProtocol(Replica replica) {
        this.replica = replica;
        this.blockTree = replica.getBlockTree();
        this.viewManager = replica.getViewManager();
        this.cryptoUtils = replica.getCryptoUtils();
        this.network = replica.getNetwork();
        this.pendingVotes = new HashSet<>();
        this.sentVotes = new HashSet<>();
    }

    public void handlePrepare(HotStuffMessageBlock msg, long senderId) {
        log("entering handlePrepare from sender=" + senderId + " msgView=" + msg.getViewNumber());

        // Verify senders view
        if (!validateSender(msg, senderId)) {
            log("rejected PREPARE: validateSender failed");
            return;
        }

        Block proposed = msg.getBlock();
        QuorumCertificateBlock qcBlock = msg.getQc();

        if (qcBlock != null && !validateQC(qcBlock)) {
            return;
        }

        if (!this.blockTree.safeNode(proposed, qcBlock)) {
            log("rejected PREPARE: safeNode failed for nodeDigest="
                + (proposed == null ? "null" : proposed.getSelfDigest()));
            return;
        }

        // Verify the blocks values in case of byzantine leader
        if (!validateBlock(proposed)) {
            log("rejected PREPARE: validateBlock failed for nodeDigest=" + (proposed == null ? "null" : proposed.getSelfDigest()));
            return;
        }
        

        log("accepted PREPARE for nodeDigest=" + proposed.getSelfDigest());
        // Reset timeout on successful PREPARE from leader (consensus progress)
        replica.getViewTimer().resetTime();

        // Vote for prepare
        phaseOneVote(Phase.PREPARE, proposed, msg.getViewNumber(), senderId);

        log("sent PREPARE vote for nodeDigest=" + proposed.getSelfDigest() + " to leader=" + senderId);

    }

    public void handlePrecommit(HotStuffMessageBlock msg, long senderId) {
        log("entering handlePrecommit from sender=" + senderId + " msgView=" + msg.getViewNumber());
        // Verify senders view
        if (!validateSender(msg, senderId)) {
            log("rejected PRECOMMIT: validateSender failed");
            return;
        }

        QuorumCertificateBlock qc = msg.getQc();

        if (!validateQC(qc)) {
            return;
        }

        blockTree.setPrepareQc(qc);

        Block node = qc.getBlock();
        log("accepted PRECOMMIT for nodeDigest=" + (node == null ? "null" : node.getSelfDigest()));
        // Reset timeout on successful PRECOMMIT from leader (consensus progress)
        replica.getViewTimer().resetTime();

        phaseOneVote(Phase.PRECOMMIT, node, msg.getViewNumber(), senderId);

        log("sent PRECOMMIT vote for nodeDigest=" + node.getSelfDigest() + " to leader=" + senderId);
    }

    public void handleCommit(HotStuffMessageBlock msg, long senderId) {
        log("entering handleCommit from sender=" + senderId + " msgView=" + msg.getViewNumber());
        // Verify senders view
        if (!validateSender(msg, senderId)) {
            log("rejected COMMIT: validateSender failed");
            return;
        }

        QuorumCertificateBlock qc = msg.getQc();

        if (!validateQC(qc)) {
            return;
        }

        blockTree.setLockedQc(qc);

        Block node = qc.getBlock();
        log("accepted COMMIT for nodeDigest=" + (node == null ? "null" : node.getSelfDigest()));
        // Reset timeout on successful COMMIT from leader (consensus progress)
        replica.getViewTimer().resetTime();

        phaseOneVote(Phase.COMMIT, node, msg.getViewNumber(), senderId);

        log("sent COMMIT vote for nodeDigest=" + node.getSelfDigest() + " to leader=" + senderId);
    }

    public void handleDecide(HotStuffMessageBlock msg, long senderId) {
        log("entering handleDecide from sender=" + senderId + " msgView=" + msg.getViewNumber());
        // Verify senders view
        if (!validateSender(msg, senderId)) {
            log("rejected DECIDE: validateSender failed");
            return;
        }

        QuorumCertificateBlock qc = msg.getQc();
        if (!validateQC(qc)) {
            return;
        }

        Block node = qc.getBlock();
        List<Transaction> transaction = node == null ? null : node.getTransactions();
        if (transaction == null || transaction.isEmpty()) {
            log("rejected DECIDE: node transaction is null");
            return;
        }

        String decideKey = node.getSelfDigest(); //removed view number from decide key to not allow duplicate decides
        if (replica.getDecidedViews().contains(decideKey)) {
            log("ignoring duplicate DECIDE for key=" + decideKey);
            return;
        }
        replica.getDecidedViews().add(decideKey);

        // Step 1: Execute the entire block
        EvmExecutionService evm = this.replica.getEvmExecutionService();
        long blockGasLimit = node.getBlockGasLimit();
        BlockExecutionResult blockResult = evm.executeBlock(transaction, blockGasLimit, (int) senderId);
        
        if (!blockResult.success()) {
            log("block execution failed: " + blockResult.error());
            return;
        }

        // Step 2: Send responses to clients based on execution results
        List<EvmExecutionService.TxExecutionResult> txResults = blockResult.txResults();
        for (int i = 0; i < transaction.size(); i++) {
            Transaction tx = transaction.get(i);
            String requestKey = tx.getTransactionHash();

            // Replay Check: if the request is already executed, ignore it
            if (!replica.markExecutedRequest(requestKey)) {
                log("duplicate commit ignored for key=" + requestKey);
                continue;
            }

            // Prevent already executed transactions from being reproposed in future views.
            replica.removePendingTransaction(requestKey);

            EvmExecutionService.TxExecutionResult txResult = i < txResults.size() ? txResults.get(i) : null;
            boolean txSuccess = txResult != null && txResult.success();

            if (txSuccess) {
                replica.commitTransaction(requestKey);
            }
            
            // Send response to client based on transaction execution result
            Replica.ClientRequestRef requestRef = replica.popClientRequest(requestKey);
            if (requestRef != null) {
                if (txSuccess) {
                    sendClientAck("client" + requestRef.clientId, requestRef.requestNonce, true, null);
                } else {
                    String errorMsg = txResult != null ? txResult.error() : "Transaction failed to execute";
                    sendClientAck("client" + requestRef.clientId, requestRef.requestNonce, false, errorMsg);
                }
            }
        }

        // Step 3: Store the entire block (all txs, successful and failed)
        Block block = convertBlock(node, blockResult);
        this.blockTree.addNode(block);
        
        replica.startViewTimer();

        phaseOneVote(Phase.DECIDE, node, msg.getViewNumber(), senderId);
        log("sent DECIDE vote for nodeDigest=" + node.getSelfDigest() + " to leader=" + senderId);
    }

    private Block convertBlock(Block sourceBlock, BlockExecutionResult blockResult) {
        if (sourceBlock == null) {
            throw new IllegalArgumentException("sourceBlock cannot be null");
        }
        if (blockResult == null) {
            throw new IllegalArgumentException("blockResult cannot be null");
        }

        Block converted = new Block();
        converted.setParentDigest(sourceBlock.getParentDigest());
        converted.setTransactions(sourceBlock.getTransactions());
        converted.setBlockGasLimit(sourceBlock.getBlockGasLimit());

        converted.setSelfDigest(sourceBlock.getSelfDigest());

        converted.setTotalGasUsed(blockResult.totalGasUsed());
        converted.setWorldStateHash(blockResult.worldStateHash());
        return converted;
    }

    private boolean validateSender(HotStuffMessageBlock msg, long senderId) {
        long msgView = msg.getViewNumber();
        log("validating sender=" + senderId + " msgView=" + msgView
            + " attemptedView=" + replica.getAttemptedViewNumber() + " currentView=" + replica.getViewNumber());
        if (msgView < replica.getAttemptedViewNumber()) {
            log("sender validation failed: stale msgView=" + msgView
                + " attemptedView=" + replica.getAttemptedViewNumber());
            return false;
        }

        if (msgView > replica.getAttemptedViewNumber()) {
            replica.setAttemptedViewNumber(msgView);
            replica.startViewTimer();
            log("advanced attemptedViewNumber to " + replica.getAttemptedViewNumber() + " based on incoming message view");
        }

        int leader = viewManager.getLeaderIdforView(msgView, replica.getTotalReplicas());

        if (senderId != leader) {
            log("sender validation failed: sender " + senderId
                    + " is not leader " + leader + " for msgView=" + msgView);
            return false;
        }
        log("sender validation passed for sender=" + senderId);
        return true;
    }

    /**
     *  Validates the block before its exucution
     */
    private boolean validateBlock(Block block){
        // Temporary saved values to confirm the order of the nonces and replays and to check the balances of the senders
        Map<String, Long> checkedNonces = new HashMap<>();
        Map<String, Long> checkedBalances = new HashMap<>();
        EvmExecutionService evm = replica.getEvmExecutionService();
        List<Transaction> txs = block.getTransactions();

        if (txs == null || txs.isEmpty()) {
            log("rejected PREPARE: proposed block has no transactions");
            return false;
        }

        for (Transaction tx : txs) {
            String sender = tx.getSender();
            long currentNonce = checkedNonces.getOrDefault(sender, evm.getNonce(sender));
            long currentBalance = checkedBalances.getOrDefault(sender, evm.getBalance(sender));

            // Check transaction signature
            if (!replica.verifyTransactionSignature(tx.getSender(), tx)) {
                log("rejected PREPARE: invalid transaction signature for tx=" + tx.getTransactionHash());
                return false;
            }
            // Check gas limit and price
            if (tx.getGasLimit() <= 0 || tx.getGasPrice() <= 0) {
                log("rejected PREPARE: invalid gas limit or price for tx=" + tx.getTransactionHash());
                return false;
            }


            // Check Worst-Case Funds (value + gasPrice * gasLimit)
            long worstCaseFee;
            long totalRequired;
            try {
                worstCaseFee = Math.multiplyExact(tx.getGasPrice(), tx.getGasLimit());
                totalRequired = Math.addExact(tx.getValue(), worstCaseFee);
            } catch (ArithmeticException e) {
                log("rejected PREPARE: arithmetic overflow while validating tx=" + tx.getTransactionHash());
                return false;
            }
            if (currentBalance < totalRequired) {
                log("rejected PREPARE: sender cannot afford value+maxFee for tx=" + tx.getTransactionHash());
                return false;
            }

            // Check Nonce Order
            if (tx.getNonce() != currentNonce) {
                log("rejected PREPARE: invalid nonce for tx=" + tx.getTransactionHash() + " expectedNonce=" + currentNonce);
                return false;
            }

            // Update the maps
            checkedNonces.put(sender, currentNonce + 1);
            checkedBalances.put(sender, currentBalance - totalRequired);
        } 
        return true;
    }

    public void handleSecondPhaseVote(AggregatedCommitmentBlock aggregatedCommitment, long senderId) {
        String key = voteKey(aggregatedCommitment.getQcType(), aggregatedCommitment.getViewNumber(),aggregatedCommitment.getNodeDigest());
        

        if (!pendingVotes.contains(key)) {
            log("ignoring phase 2 vote without pending phase-1 context for key" + key);
            return;
        }

        int leader = viewManager.getLeaderIdforView(aggregatedCommitment.getViewNumber(), replica.getTotalReplicas());
        if (senderId != leader) {
            log("ignoring phase 2 vote from non-leader sender" + senderId + " expectedLeader" + leader);
            return;
        }

        if (aggregatedCommitment.getSignerIndices() == null
                || !aggregatedCommitment.getSignerIndices().contains((long) replica.getReplicaId())) {
            log("ignoring phase 2 vote: replica not selected signer for key=" + key);
            return;
        }

        String partialSignature = cryptoUtils.createPartialSignature(aggregatedCommitment.getQcType(), aggregatedCommitment.getViewNumber(), aggregatedCommitment.getNodeDigest(), aggregatedCommitment.getAggregatedCommitment(), aggregatedCommitment.getSignerIndices());

        sendVote(aggregatedCommitment.getQcType(), aggregatedCommitment.getViewNumber(), aggregatedCommitment.getNodeDigest(), partialSignature, senderId);
        pendingVotes.remove(key);
        log("sent phase-2 partial signature for key" + key + " to leader" + senderId);
    }

    private void log(String msg) {
        System.out.println("Replica " + replica.getReplicaId() + " " + msg);
    }

    private void phaseOneVote(Phase qcType, Block node, long viewNumber, long leaderId) {
        if (node == null) {
            log("cannot start phase-1 vote: node is null");
            return;
        }

        String nodeDigest = node.getSelfDigest();
        String key = voteKey(qcType, viewNumber, nodeDigest);
        pendingVotes.add(key);

        String nonceCommitment = cryptoUtils.createNonceCommitment(qcType, viewNumber, nodeDigest);

        EnvelopeCommitment env = new EnvelopeCommitment();
        env.setSenderId(replica.getReplicaId());

        CommitementBlock commitment = new CommitementBlock();
        commitment.setQcType(qcType);
        commitment.setViewNumber(viewNumber);
        commitment.setNodeDigest(nodeDigest);
        commitment.setNonceCommitment(nonceCommitment);
        env.setPhaseOneVote(commitment);
        network.send(env, leaderId);
        log("sent phase1 commitment for key=" + key + " to leader=" + leaderId);
    }

    private void sendVote(Phase phase, long view, String nodeDigest, String partialSignature, long leaderId) {
        String voteKey = voteKey(phase, view, nodeDigest);
        if (!sentVotes.add(voteKey)) {
            log("duplicate vote suppressed for " + voteKey);
            return;
        }

        VoteBlock vote = new VoteBlock();
        vote.setQcType(phase);
        vote.setViewNumber(view);
        vote.setNodeDigest(nodeDigest);
        vote.setPartialSignature(partialSignature);

        EnvelopeVote env = new EnvelopeVote();
        env.setSenderId(replica.getReplicaId());
        env.setVote(vote);

        network.send(env, leaderId);
    }

    private String voteKey(Phase phase, long viewNumber, String nodeDigest) {
        return phase.name() + ":" + viewNumber + ":" + nodeDigest;
    }

    private boolean validateQC(QuorumCertificateBlock qc) {
        if (qc == null) {
            return false;
        }

        if (!cryptoUtils.verifyQCSignature(qc)) {
            return false;
        }
        return true;
    }

    private void sendClientAck(String clientId, long requestNonce, boolean success, String errorMsg) {
        EnvelopeResponse response = new EnvelopeResponse();
        response.setSenderId(replica.getReplicaId());
        response.setRequestNonce(requestNonce);
        
        if (success) {
            response.setMessage("ACK");
        } else {
            response.setMessage("NACK: " + (errorMsg != null ? errorMsg : "Transaction failed"));
        }
        
        network.sendToPeer(response, clientId);
        log("sent client response to " + clientId + " requestNonce=" + requestNonce + " success=" + success);
    }
    
}