package com.depchain.consensus;

import com.depchain.network.envelope.HotStuffMessageBlock;
import com.depchain.network.envelope.VoteBlock;
import com.depchain.blockchain.model.Block;

public class FaultInjector {
    
    private ByzantineConfig config;
    private boolean silentMode = false;
    private Replica replica;
    
    public FaultInjector(ByzantineConfig config, Replica replica) {
        this.config = config;
        this.replica = replica;
        
        if (config.getType() == ByzantineConfig.ByzantineEnumType.SILENT) {
            this.silentMode = true;
            System.out.println("[BYZANTINE] Replica entering PERMANENT SILENT mode");
        }
    }
    

    public boolean shouldBeSilent() {
        return silentMode;
    }
    
    public void processIncomingMessage(String phase) {
        if (!config.isEnabled() || !config.shouldAffectPhase(phase)) {
            return;
        }
        
        switch (config.getType()) {
            case DELAY:
                try {
                    long delay = config.getDelayMs();
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                break;
            default:
                break;
        }
        
        return;
    }
    

    public HotStuffMessageBlock processOutgoingMessage(HotStuffMessageBlock block, String phase) {
        if (!config.isEnabled() || !config.shouldAffectPhase(phase)) {
            return block;
        }
        
        switch (config.getType()) {
            case DELAY:
                try {
                    Thread.sleep(config.getDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                break;
    
            case CONFLICT:
                Block blockData = block.getBlock();
                if (blockData != null && blockData.getTransactions() != null) {
                    blockData.setTransactions(null);
                }
                System.out.println("[BYZANTINE] Replica " + this.replica.getReplicaId() + " creating conflicting block for phase " + phase);

                HotStuffMessageBlock conflictBlock = new HotStuffMessageBlock();
                conflictBlock.setMessageType(config.getConsensusPhases());
                conflictBlock.setViewNumber(replica.getViewNumber());  
                conflictBlock.setBlock(blockData);
                conflictBlock.setQc(block.getQc());

                return conflictBlock;
            default:
                break;
        }
        
        return block;
    }

    public void processIncomingMessageLeader(String phase) {
        if (!config.isEnabled() || !config.shouldAffectPhase(phase)) {
            return;
        }
        
        switch (config.getType()) {
            case DELAY:
                try {
                    long delay = config.getDelayMs();
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                break;
            default:
                break;
        }
        
        return;
    }

    public VoteBlock processOutgoingMessageLeader(VoteBlock block, String phase) {
        if (!config.isEnabled() || !config.shouldAffectPhase(phase)) {
            return block;
        }
        
        switch (config.getType()) {
            case DELAY:
                try {
                    Thread.sleep(config.getDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                break;
            
            case CONFLICT:
                VoteBlock conflictVote = new VoteBlock();
                conflictVote.setQcType(block.getQcType());
                conflictVote.setViewNumber(block.getViewNumber());
                
                String corruptedDigest = block.getNodeDigest().substring(0, 30) + "CORRUPTED";
                conflictVote.setNodeDigest(corruptedDigest);
                System.out.println("[BYZANTINE LEADER] Replica " + this.replica.getReplicaId() + 
                        " creating conflicting vote for phase " + phase + 
                        " | Original digest: " + block.getNodeDigest() + 
                        " | Corrupted digest: " + corruptedDigest);
                
                return conflictVote;
            default:
                break;
        }
        
        return block;
    }
}
