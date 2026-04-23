package com.depchain.blockchain;

import java.util.Map;
import java.util.HashMap;

import com.depchain.blockchain.model.Transaction;
import com.depchain.network.envelope.NodeBlock;
import com.depchain.network.envelope.QuorumCertificateBlock;
import com.depchain.blockchain.model.Block;

public class BlockTree {
    private Block genesis;
    private Map<String, Block> nodeMap;
    private QuorumCertificateBlock lockedQC;
    private QuorumCertificateBlock prepareQC;

    public BlockTree(Block genesisBlock) {
        if (genesisBlock == null) {
            throw new IllegalArgumentException("Genesis block cannot be null");
        }
        this.nodeMap = new HashMap<String, Block>();
        this.genesis = genesisBlock;
        nodeMap.put(this.genesis.getSelfDigest(), this.genesis);
    }

    public boolean extendsFrom(Block node, Block justifyNode) {
        Block current = node;

        while (current != null) {
            if (current.getSelfDigest().equals(justifyNode.getSelfDigest())) {
                return true;
            }
            current = getNodeByDigest(current.getParentDigest());
        }

        return false;
    }

    // The safety rule to accept a proposal is the branch of mnode extends from the
    // currently locked node lockedQCnode.
    // The liveness rule is the replica will accept m if mjustify has a higher view
    // than the current lockedQC.
    // The predicate is true as long as either one of two rules holds.
    public boolean safeNode(Block node, QuorumCertificateBlock justifyQC) {
        // if there is no lockedQC it's safe
        if (this.lockedQC == null) {
            return true;
        }

        Block lockedBlock = lockedQC.getBlock();

        // safety
        boolean extendsLocked = extendsFrom(node, lockedBlock);

        // liveness
        boolean higherQC = justifyQC.getViewNumber() > lockedQC.getViewNumber();

        return extendsLocked || higherQC;
    }

    public void addNode(Block node) {
        this.nodeMap.put(node.getSelfDigest(), node);
    }

    public void setPrepareQc(QuorumCertificateBlock qc) {
        this.prepareQC = qc;
    }

    public void setLockedQc(QuorumCertificateBlock qc) {
        this.lockedQC = qc;
    }

    public QuorumCertificateBlock getLockedQc() {
        return this.lockedQC;
    }

    public Block getNodeByDigest(String digest) {
        return this.nodeMap.get(digest);
    }

    public Block getGenesis() {
        return genesis;
    }
}
