package com.depchain.crypto;

import com.depchain.network.envelope.Phase;
import com.depchain.network.envelope.QuorumCertificateBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class CryptoUtils {
    private ThresholdSignatures thresholdSignatures;
    private String THRESHOLD_CONFIG_DIR = "../security/threshold";

    public CryptoUtils(int replicaId) {
        try {
            Path configPath = Path.of(THRESHOLD_CONFIG_DIR, "node" + (replicaId + 1) + "-threshold.json");
            this.thresholdSignatures = new ThresholdSignatures(configPath.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize threshold signatures", e);
        }
    }

    public String createNonceCommitment(Phase qcType, long viewNumber, String nodeDigest) {
        return thresholdSignatures.createNonceCommitment(qcType, viewNumber, nodeDigest);
    }

    public String aggregateCommitments(Set<String> nonceCommitments) {
        try {
            return thresholdSignatures.aggregateCommitments(nonceCommitments);
        } catch (Exception e) {
            throw new RuntimeException("Failed to aggregate commitments", e);
        }
    }

    public String createPartialSignature(Phase qcType, long viewNumber, String nodeDigest, String aggregatedCommitment, Set<Long> signerIndices) {
        return thresholdSignatures.createPartialSignature(qcType, viewNumber, nodeDigest, aggregatedCommitment, signerIndices);
    }

    public String createQuorumCertificate(String aggregatedCommitment, Set<String> partialSignatures) {
        return thresholdSignatures.createQuorumCertificate(aggregatedCommitment, partialSignatures);
    }

    public boolean verifyQCSignature(QuorumCertificateBlock qc) {
        return thresholdSignatures.verifyQuorumCertificate(qc);
    }
}