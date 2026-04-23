package com.depchain.consensus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

import com.depchain.blockchain.BlockTree;
import com.depchain.blockchain.model.Transaction;
import com.depchain.blockchain.model.Block;
import com.depchain.crypto.CryptoUtils;
import com.depchain.network.Network;
import com.depchain.network.envelope.AggregatedCommitmentBlock;
import com.depchain.network.envelope.CommitementBlock;
import com.depchain.network.envelope.EnvelopeAggregatedCommitment;
import com.depchain.network.envelope.EnvelopeMessage;
import com.depchain.network.envelope.HotStuffMessageBlock;
import com.depchain.network.envelope.Phase;
import com.depchain.network.envelope.QuorumCertificateBlock;
import com.depchain.network.envelope.VoteBlock;
import com.depchain.state.EvmExecutionService;
import com.depchain.state.EvmExecutionService.BlockExecutionResult;
import com.depchain.state.WorldStateHash;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;

public class LeaderLogic {
    private static final long BLOCK_GAS_LIMIT = Block.MAX_BLOCK_GAS;
    
    private Replica replica;

    private List<Transaction> pendingTransactions;
    private Map<String, Map<Long, VoteBlock>> prepareVotes;
    private Map<String, Map<Long, VoteBlock>> preCommitVotes;
    private Map<String, Map<Long, VoteBlock>> commitVotes;
    private Map<Long, List<HotStuffMessageBlock>> newViewMsgs;
    private Map<Long, Set<Long>> newViewSenders;
    private Map<String, Set<Long>> prepareVoteSenders;
    private Map<String, Set<Long>> preCommitVoteSenders;
    private Map<String, Set<Long>> commitVoteSenders;

    // Threshold signature 
    private Map<String, Map<Long, String>> pendingCommitments;
    private Map<String, String> aggregatedCommitments;
    private Map<String, Set<Long>> aggregatedCommitmentSigners;

    private Set<Long> startedViews;
    private Set<String> decidedViews;

    //once quorum ignore subsequent votes that would retrigger the transition
    private Set<String> phaseAdvanced;

    private BlockTree blockTree;
    private ViewManager viewManager;
    private CryptoUtils cryptoUtils;
    private Network network;

    public LeaderLogic(Replica replica) {
        this.replica = replica;
        this.blockTree = replica.getBlockTree();
        this.viewManager = replica.getViewManager();
        this.cryptoUtils = replica.getCryptoUtils();
        this.network = replica.getNetwork();
        
        // leader only structs
        this.pendingTransactions = replica.getPendingTransactions();
        this.prepareVotes = new HashMap<>();
        this.preCommitVotes = new HashMap<>();
        this.commitVotes = new HashMap<>();
        this.newViewMsgs = new HashMap<>();
        this.newViewSenders = new HashMap<>();
        this.prepareVoteSenders = new HashMap<>();
        this.preCommitVoteSenders = new HashMap<>();
        this.commitVoteSenders = new HashMap<>();
        this.startedViews = new HashSet<>();
        this.decidedViews = replica.getDecidedViews();
        this.pendingCommitments = new HashMap<>();
        this.aggregatedCommitments = new HashMap<>();
        this.aggregatedCommitmentSigners = new HashMap<>();
        this.phaseAdvanced = new HashSet<>();
    }

    private void log(String msg) {
        System.out.println("Replica " + replica.getReplicaId() + " " + msg);
    }

    public void handleNewView(HotStuffMessageBlock msg, long senderId) {
        Long view = msg.getViewNumber();
        log("received NEW_VIEW for view=" + view);

        int leader = viewManager.getLeaderIdforView(view, replica.getTotalReplicas());

        // 1. Check if this replica is leader, if not return
        if (leader != replica.getReplicaId()) {
                log("ignored NEW_VIEW for view=" + view + " because leader is " + leader);
            return;
        }

        this.newViewMsgs.putIfAbsent(view, new ArrayList<>());
        this.newViewSenders.putIfAbsent(view, new HashSet<>());

        // 2. Check for duplicate messages
        if (!this.newViewSenders.get(view).add(senderId)) {
            log("ignored duplicate NEW_VIEW from sender=" + senderId + " for view=" + view);
            return;
        }

        // 3. Adding newViewMsg
        this.newViewMsgs.get(view).add(msg);
        log("NEW_VIEW count for view=" + view + " is " + this.newViewMsgs.get(view).size() + "/" + getQuorumSize());

        // 4. Checking if newViewMsg quorum was reached
        if (this.newViewMsgs.get(view).size() < getQuorumSize()) {
            log("NEW_VIEW quorum not reached yet for view=" + view);
            return;
        }

        // 5. If newViewMsg quorum was reached start view
        if (this.startedViews.contains(view)) {
            log("already started view=" + view + ", ignoring duplicate NEW_VIEW quorum event");
            return;
        }

        log("NEW_VIEW quorum reached for view=" + view + ", starting view");
        startView(view);
        replica.getViewTimer().resetTime(); 
        replica.startViewTimer();
    }

    private int getQuorumSize() {
        int f = (replica.getTotalReplicas() - 1) / 3;
        int quorum = 2 * f + 1;
        log("computed quorum size=" + quorum + " with totalReplicas=" + replica.getTotalReplicas() + " f=" + f);
        return quorum;
    }

    public void startView(long view) {
        log("entering startView for view=" + view);

        if (!markViewStarted(view)) {
            return;
        }

        replica.setViewNumber(view);

        List<HotStuffMessageBlock> msgs = getNewViewMessages(view);
        if (msgs == null) {
            return;
        }

        QuorumCertificateBlock highQC = selectHighQC(msgs);
        Block parent = getParentNode(highQC);
        Block block = createProposalNode(parent);

        List<Transaction> transactions = pickPendingTransaction(BLOCK_GAS_LIMIT);
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        block.setTransactions(transactions);
        block.setTotalGasUsed((long) 0); // update with executions
        // only set world state hash after execution when decided
        block.setSelfDigest(block.calculateBlockDigest(block)); 

        blockTree.addNode(block);
        log("added proposed node with digest=" + block.getSelfDigest());

        broadcastProposal(view, block, highQC);
    }

    private boolean markViewStarted(long view) {
        if (startedViews.contains(view)) {
            log("startView skipped: already started view=" + view);
            return false;
        }
        startedViews.add(view);
        return true;
    }

    private List<HotStuffMessageBlock> getNewViewMessages(long view) {
        List<HotStuffMessageBlock> msgs = newViewMsgs.get(view);
        if (msgs == null || msgs.isEmpty()) {
            log("startView aborted: no NEW_VIEW messages stored for view=" + view);
            return null;
        }
        log("startView processing " + msgs.size() + " NEW_VIEW messages");
        return msgs;
    }

    private QuorumCertificateBlock selectHighQC(List<HotStuffMessageBlock> msgs) {
        QuorumCertificateBlock highQC = null;
        for (HotStuffMessageBlock block : msgs) {
            QuorumCertificateBlock qc = block.getQc();
            if (qc == null) {
                continue;
            }
            if (highQC == null || qc.getViewNumber() > highQC.getViewNumber()) {
                highQC = qc;
            }
        }
        log("selected highQC view=" + (highQC == null ? "genesis" : highQC.getViewNumber()));
        return highQC;
    }

    private Block getParentNode(QuorumCertificateBlock highQC) {
        Block parent = highQC == null ? blockTree.getGenesis() : highQC.getBlock();
        log("using parent digest=" + parent.getSelfDigest());
        return parent;
    }

    private Block createProposalNode(Block parent) {
        Block block = new Block();
        block.setParentDigest(parent.getSelfDigest());
        return block;
    }

    /**
     * Selects pending transactions to include in the proposed block.
     * The selection is based on the max fee (gasPrice * gasLimit) of the transactions, while respecting the block's gas limit
     * and ensuring that for each sender, transactions are included in nonce order.
     */
    private List<Transaction> pickPendingTransaction(long blockGasLimit) {
        // Group pending transactions by sender
        Map<String, List<Transaction>> bySender = new HashMap<>();
        for (Transaction tx : pendingTransactions) {
            bySender.computeIfAbsent(tx.getSender(), k -> new ArrayList<>()).add(tx);
        }
        
        // Sort each sender's list by nonce 
        for (List<Transaction> list : bySender.values()) {
            list.sort(Comparator.comparingLong(Transaction::getNonce));
        }

        List<Transaction> selectedTransactions = new ArrayList<>();
        long totalGasSum = 0;

        while (totalGasSum < blockGasLimit) {
            Transaction bestTx = null;
            long highestMaxFee = -1;

            // Compare the max fee of the first transaction of each sender (gasPrice * gasLimit)
            for (Map.Entry<String, List<Transaction>> entry : bySender.entrySet()) {
                List<Transaction> senderTxs = entry.getValue();
                if (senderTxs.isEmpty()) continue;

                Transaction candidate = senderTxs.get(0);
                long candidateMaxFee = candidate.getGasPrice() * candidate.getGasLimit();
                
                if (candidateMaxFee > highestMaxFee) {
                    if (totalGasSum + candidate.getGasLimit() <= blockGasLimit) {
                        bestTx = candidate;
                        highestMaxFee = candidateMaxFee;
                    }
                    else {
                        break; // block gas limit exceeded, needs to follow the max fee priority
                    }
                }
            }
            if (bestTx == null) break; // No more pending transactions
            selectedTransactions.add(bestTx);
            totalGasSum += bestTx.getGasLimit();
            bySender.get(bestTx.getSender()).remove(0); // Remove the selected transaction from its sender's queue
        }

        return selectedTransactions;
    }

    private void broadcastProposal(long view, Block block, QuorumCertificateBlock highQC) {
        HotStuffMessageBlock proposal = new HotStuffMessageBlock();
        proposal.setMessageType(Phase.PREPARE);
        proposal.setBlock(block);
        proposal.setQc(highQC);
        proposal.setViewNumber(view);

        EnvelopeMessage envelopeMessage = new EnvelopeMessage();
        envelopeMessage.setSenderId(replica.getReplicaId());
        envelopeMessage.setHotStuffMessageBlock(proposal);

        network.broadcast(envelopeMessage);
        log("broadcast PREPARE for view=" + view + " nodeDigest=" + block.getSelfDigest());
    }

    public void handleFirstPhaseVote(long senderId, CommitementBlock commitment) {
        log("handling first phase vote");
        if (commitment == null) {
            log("ignoring null first-phase commitment from sender=" + senderId);
            return;
        }

        String key = voteKey(commitment.getQcType(), commitment.getViewNumber(), commitment.getNodeDigest());
        pendingCommitments.putIfAbsent(key, new HashMap<>());
        pendingCommitments.get(key).put(senderId, commitment.getNonceCommitment());

        int commitmentCount = pendingCommitments.get(key).size();
        if (aggregatedCommitments.containsKey(key)) {
            log("aggregated commitment already broadcast for key=" + key);
            return;
        }

        if (commitmentCount < getQuorumSize()) {
            log("not enough nonce commitments yet" + commitmentCount + "/" + getQuorumSize());
            return;
        }

        log("received first phase quorum, broadcasting aggregated commitment");
        Map<Long, String> signersCommitments = pendingCommitments.get(key);
        Set<String> nonceCommitments = new HashSet<>(signersCommitments.values());
        Set<Long> signerIndices = new HashSet<>(signersCommitments.keySet());
        String aggregatedCommitment = cryptoUtils.aggregateCommitments(nonceCommitments);
        aggregatedCommitments.put(key, aggregatedCommitment);
        aggregatedCommitmentSigners.put(key, new HashSet<>(signerIndices));

        EnvelopeAggregatedCommitment env = new EnvelopeAggregatedCommitment();
        env.setSenderId(replica.getReplicaId());
        AggregatedCommitmentBlock aggregatedBlock = new AggregatedCommitmentBlock();
        aggregatedBlock.setQcType(commitment.getQcType());
        aggregatedBlock.setViewNumber(commitment.getViewNumber());
        aggregatedBlock.setNodeDigest(commitment.getNodeDigest());
        aggregatedBlock.setAggregatedCommitment(aggregatedCommitment);
        aggregatedBlock.setSignerIndices(signerIndices);
        env.setPhaseTwoVote(aggregatedBlock);
        network.broadcast(env);

        log("broadcast aggregated commitment for key=" + key);
    }

    public void handlePrepareVote(VoteBlock vote, long senderId) {
        log("received PREPARE vote for " + vote.getNodeDigest());
        handleVote(prepareVotes, prepareVoteSenders, vote, senderId, Phase.PREPARE, Phase.PRECOMMIT, false);
    }

    public void handlePrecommitVote(VoteBlock vote, long senderId) {
        log("received PRECOMMIT vote for " + vote.getNodeDigest());
        handleVote(preCommitVotes, preCommitVoteSenders, vote, senderId, Phase.PRECOMMIT, Phase.COMMIT, false);
    }

    public void handleCommitVote(VoteBlock vote, long senderId) {
        log("received COMMIT vote for " + vote.getNodeDigest());
        handleVote(commitVotes, commitVoteSenders, vote, senderId, Phase.COMMIT, Phase.DECIDE, true);
    }

    private void handleVote(Map<String, Map<Long, VoteBlock>> voteMap, Map<String, Set<Long>> voteSenders, VoteBlock vote,
            long senderId, Phase currentPhase, Phase nextPhase, boolean commitLocally) {
        if (vote.getViewNumber() != replica.getViewNumber()) {
            log("rejected " + currentPhase + " vote: wrong viewNumber voteView=" + vote.getViewNumber()
                    + " currentView=" + replica.getViewNumber());
            return;
        }

        String nodeDigest = vote.getNodeDigest();
        String roundKey = voteKey(currentPhase, vote.getViewNumber(), nodeDigest);
        String dedupKey = currentPhase + ":" + vote.getViewNumber() + ":" + nodeDigest;
        voteSenders.putIfAbsent(dedupKey, new HashSet<>());
        if (!voteSenders.get(dedupKey).add(senderId)) {
            log("ignored duplicate " + currentPhase + " vote from sender=" + senderId + " for " + dedupKey);
            return;
        }

        voteMap.putIfAbsent(roundKey, new HashMap<>());
        voteMap.get(roundKey).put(senderId, vote);

        log(currentPhase + " vote count for " + nodeDigest + " = " + voteMap.get(roundKey).size() + "/" + getQuorumSize());

        if (voteMap.get(roundKey).size() < getQuorumSize()) {
            log(currentPhase + " quorum not reached for " + nodeDigest);
            return;
        }
        // Prevent processing multiple times the transition to the next phase
        if (phaseAdvanced.contains(roundKey)) {
            log("already advanced " + currentPhase + " for nodeDigest=" + nodeDigest + " view=" + vote.getViewNumber()
                    + ", ignoring additional votes");
            return;
        }
        phaseAdvanced.add(roundKey);

        log(currentPhase + " quorum reached for " + nodeDigest);
        replica.getViewTimer().resetTime();

        Block node = blockTree.getNodeByDigest(nodeDigest);
        if (node == null) {
            log("cannot build " + currentPhase + " QC: node not found for digest=" + nodeDigest);
            return;
        }

        if (commitLocally) {
            String decideKey = replica.getViewNumber() + ":" + node.getSelfDigest();
            if (decidedViews.contains(decideKey)) {
                log("already decided key=" + decideKey + ", skipping duplicate DECIDE broadcast/commit");
                return;
            }
            decidedViews.add(decideKey);
            List<Transaction> transactions = node.getTransactions();
            if (transactions == null || transactions.isEmpty()) {
                log("cannot commit locally as leader: node transaction is null for digest=" + node.getSelfDigest());
                return;
            }
            for (Transaction tx : transactions) {
                replica.commitTransaction(tx.getTransactionHash());
                log("committed transaction locally as leader=" + tx.getTransactionHash() + " nodeDigest=" + node.getSelfDigest());
            }
            
            // Step 1: Execute the entire block
            EvmExecutionService evm = this.replica.getEvmExecutionService();
            long blockGasLimit = node.getBlockGasLimit();
            BlockExecutionResult blockResult = evm.executeBlock(transactions, blockGasLimit, replica.getReplicaId());
            
            if (!blockResult.success()) {
                log("block execution failed: " + blockResult.error());
                return;
            }

            Block block = convertBlock(node, blockResult);
            this.blockTree.addNode(block);

            writeBlock(block);

            //Dump the world state after the block to file
            WorldStateHash.writeWorldStateToFile(evm.getWorld(), replica.getViewNumber());

            replica.getViewTimer().resetTime(); 
            replica.startViewTimer();
        }

        QuorumCertificateBlock qc = new QuorumCertificateBlock();
        qc.setQcType(currentPhase.toString());
        qc.setBlock(node);
        qc.setViewNumber(replica.getViewNumber());

        String aggregatedCommitment = aggregatedCommitments.get(roundKey);
        if (aggregatedCommitment == null) {
            log("cannot build " + currentPhase + " QC: missing aggregated commitment for nodeDigest=" + nodeDigest);
            return;
        }

        Set<Long> expectedSigners = aggregatedCommitmentSigners.get(roundKey);
        if (expectedSigners == null || expectedSigners.isEmpty()) {
            log("cannot build " + currentPhase + " QC: missing signer set for nodeDigest=" + nodeDigest);
            return;
        }

        Set<String> partialSignatures = new HashSet<>();
        for (Map.Entry<Long, VoteBlock> collected : voteMap.get(roundKey).entrySet()) {
            if (expectedSigners.contains(collected.getKey())) {
                partialSignatures.add(collected.getValue().getPartialSignature());
            }
        }

        if (partialSignatures.size() < getQuorumSize()) {
            log("cannot build " + currentPhase + " QC yet: collected signed partials=" + partialSignatures.size()
                    + " expectedQuorum=" + getQuorumSize() + " nodeDigest=" + nodeDigest);
            return;
        }
        qc.setAggregateSignature(cryptoUtils.createQuorumCertificate(aggregatedCommitment, partialSignatures));

        HotStuffMessageBlock msg = new HotStuffMessageBlock();
        msg.setMessageType(nextPhase);
        msg.setQc(qc);
        msg.setViewNumber(replica.getViewNumber());

        EnvelopeMessage envelopeMessage = new EnvelopeMessage();
        envelopeMessage.setSenderId(replica.getReplicaId());
        envelopeMessage.setHotStuffMessageBlock(msg);

        network.broadcast(envelopeMessage);
        log("broadcast " + nextPhase + " for nodeDigest=" + nodeDigest);

        // Minimal cleanup so these maps don't grow forever
        voteMap.remove(roundKey);
        voteSenders.remove(dedupKey);
        pendingCommitments.remove(roundKey);
        aggregatedCommitments.remove(roundKey);
        aggregatedCommitmentSigners.remove(roundKey);
    }

    private String voteKey(Phase phase, long viewNumber, String nodeDigest) {
        return phase.name() + "|" + viewNumber + "|" + nodeDigest;
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

    private void writeBlock(Block block) {
        try {
            File blocksDir = new File("depchain/blocks");
            if (!blocksDir.exists()) {
                blocksDir.mkdirs();
            }

            // Create JSON 
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> blockData = new HashMap<>();
            blockData.put("viewNumber", replica.getViewNumber());
            blockData.put("blockHash", block.getSelfDigest());
            blockData.put("parentHash", block.getParentDigest());
            blockData.put("totalGasUsed", block.getTotalGasUsed());
            blockData.put("worldStateHash", block.getWorldStateHash());
            blockData.put("blockGasLimit", block.getBlockGasLimit());
            blockData.put("transactions", block.getTransactions());

            String filename = "depchain/blocks/" + replica.getViewNumber() + ".json";
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filename), blockData);
            
            log("wrote block to file: " + filename);
        } catch (Exception e) {
            System.err.println("Replica " + replica.getReplicaId() + " failed to write block to JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
