package com.depchain.consensus;

import com.depchain.network.envelope.*;
import com.depchain.blockchain.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MessageHandler {
    private Replica replica;
    private LeaderLogic leaderLogic;
    private ReplicaProtocol replicaProtocol;
    private FaultInjector faultInjector;
    private ObjectMapper objectMapper = new ObjectMapper();

    public MessageHandler(Replica replica) {
        this(replica, new FaultInjector(new ByzantineConfig(), replica));
    }

    public MessageHandler(Replica replica, FaultInjector faultInjector) {
        this.replica = replica;
        this.faultInjector = faultInjector;
        this.leaderLogic = new LeaderLogic(replica);
        this.replicaProtocol = new ReplicaProtocol(replica);
    }

    public synchronized void handle(Envelope env, boolean byzantineMode) {
        System.out.println("Replica " + this.replica.getReplicaId() + " dispatching envelope type=" + env.getClass().getSimpleName());
        if (env instanceof EnvelopeMessage msg) {

            HotStuffMessageBlock block = msg.getHotStuffMessageBlock();
            long senderId = msg.getSenderId();

            System.out.println("Replica " + this.replica.getReplicaId() + " received phase=" + block.getMessageType() + " from sender="
                    + senderId + " msgView=" + block.getViewNumber());
            
            switch (block.getMessageType()) {
                case PREPARE:
                    if (byzantineMode) {
                        block = handleByzantineIncoming(block, "PREPARE"); 
                    } 
                    replicaProtocol.handlePrepare(block, senderId);
                    break;
                case PRECOMMIT:
                    if (byzantineMode) {
                        block =handleByzantineIncoming(block, "PRECOMMIT");
                    }
                    replicaProtocol.handlePrecommit(block, senderId);
                    break;
                case COMMIT:
                    if (byzantineMode) {
                        block =handleByzantineIncoming(block, "COMMIT");
                    }
                    replicaProtocol.handleCommit(block, senderId);
                    break;
                case DECIDE:
                    if (byzantineMode) {
                        block =handleByzantineIncoming(block, "DECIDE");
                    }
                    replicaProtocol.handleDecide(block, senderId);
                    replica.triggerNextView();
                    break;
                case NEW_VIEW:
                    leaderLogic.handleNewView(block, senderId);
                    break;
            }
        }

        if (env instanceof EnvelopeVote vote) {
            VoteBlock block = vote.getVote();
            long senderId = vote.getSenderId();
            System.out.println("Replica " + this.replica.getReplicaId() + " received vote phase=" + block.getQcType() + " nodeDigest="
                    + block.getNodeDigest() + " voteView=" + block.getViewNumber());
            switch (block.getQcType()) {
                case PREPARE:
                    if (byzantineMode) {
                        block = handleByzantineLeader(block, "PREPARE");
                    }
                    leaderLogic.handlePrepareVote(block, senderId);
                    break;
                case PRECOMMIT:
                    if (byzantineMode) {
                        block = handleByzantineLeader(block, "PRECOMMIT");
                    }
                    leaderLogic.handlePrecommitVote(block, senderId);
                    break;
                case COMMIT:
                    if (byzantineMode) {
                        block = handleByzantineLeader(block, "COMMIT");
                    }
                    leaderLogic.handleCommitVote(block, senderId);
                    break;
                case DECIDE:
                    System.out.println(
                            "Replica " + this.replica.getReplicaId() + " received DECIDE vote in dispatch; no handler configured");
                    break;
                case NEW_VIEW:
                    System.out.println(
                            "Replica " + this.replica.getReplicaId() + " received NEW_VIEW vote in dispatch; ignoring invalid vote type");
                    break;
            }
        }

        if (env instanceof EnvelopeAppend append) {
           Transaction tx = append.getTransaction();
            try {
                if (tx.getSignature() != null && replica.verifyTransactionSignature("client"+append.getSenderId(), tx)) {
                    String expectedSender = "client" + append.getSenderId();
                    if (!expectedSender.equals(tx.getSender())) {
                        System.err.println("Replica " + replica.getReplicaId() + " rejected tx: sender mismatch tx.sender="
                                + tx.getSender() + " envelope.sender=" + expectedSender);
                        return;
                    }

                    replica.registerClientRequest(tx, append.getSenderId());
                    System.out.println("Replica " + replica.getReplicaId() + " queued client command");
                } else {
                    System.err.println("Replica " + replica.getReplicaId() + " received invalid or missing client signature from client " + append.getSenderId());
                }
            } catch (Exception e) {
                System.err.println("Replica " + replica.getReplicaId() + " failed to process client signature: " + e.getMessage());
            }
        }

        if (env instanceof EnvelopeCommitment commitment) {
            System.out.println("Leader received COMMITMENT envelope");
            leaderLogic.handleFirstPhaseVote(commitment.getSenderId(), commitment.getPhaseOneVote());
        }

        if (env instanceof EnvelopeAggregatedCommitment aggregatedCommitment) {
            System.out.println("Replica " + this.replica.getReplicaId() + " received AGGREGATED_COMMITMENT");
            replicaProtocol.handleSecondPhaseVote(aggregatedCommitment.getPhaseTwoVote(), aggregatedCommitment.getSenderId());
        }

        if (env instanceof EnvelopeNonceRequest nonceReq) {
            long targetClient = nonceReq.getNonceTargetClient();
            long currentNonce = this.replica.getEvmExecutionService().getNonce("client" + targetClient);
            EnvelopeResponse response = new EnvelopeResponse();
            response.setSenderId(this.replica.getReplicaId());
            response.setRequestNonce(-1); 
            response.setMessage("NONCE:" + currentNonce);
            this.replica.getNetwork().sendToPeer(response, "client" + nonceReq.getSenderId());
        }
    }

    public synchronized void handleNewView(HotStuffMessageBlock block) {
        leaderLogic.handleNewView(block, this.replica.getReplicaId());
    }

    public HotStuffMessageBlock handleByzantineIncoming(HotStuffMessageBlock block, String phase) {
        //silent
        if (faultInjector.shouldBeSilent()) {
            System.out.println("[BYZANTINE] Replica " + this.replica.getReplicaId() + " ignoring message");
            return null;
        }

        faultInjector.processIncomingMessage(phase);

        block = faultInjector.processOutgoingMessage(block, phase);
        return block;
    }

    public VoteBlock handleByzantineLeader(VoteBlock block, String phase) {
        //silent
        if (faultInjector.shouldBeSilent()) {
            System.out.println("[BYZANTINE] Replica " + this.replica.getReplicaId() + " ignoring message");
            return null;
        }

        faultInjector.processIncomingMessageLeader(phase);

        block = faultInjector.processOutgoingMessageLeader(block, phase);
        return block;
    }

}