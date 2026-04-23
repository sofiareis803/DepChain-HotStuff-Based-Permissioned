package com.depchain.crypto;

import com.depchain.network.envelope.Phase;
import com.depchain.network.envelope.QuorumCertificateBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weavechain.curve25519.CompressedEdwardsY;
import com.weavechain.curve25519.EdwardsPoint;
import com.weavechain.curve25519.InvalidEncodingException;
import com.weavechain.curve25519.Scalar;
import com.weavechain.sig.ThresholdSigEd25519;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThresholdSignatures {
    private final ThresholdNodeConfig config;
    private final ThresholdSigEd25519 thresholdSig;
    private final Scalar privateShare;
    private final byte[] aggregatePublicKey;
    private final Map<String, Scalar> pendingNonces = new HashMap<>();

    public ThresholdSignatures(ThresholdNodeConfig config) {
        this.config = config;
        this.thresholdSig = new ThresholdSigEd25519(config.getThreshold(), config.getTotalReplicas());
        this.privateShare = Scalar.fromBits(Base64.getDecoder().decode(config.getPrivateShare()));
        this.aggregatePublicKey = Base64.getDecoder().decode(config.getAggregatePublicKey());
    }

    public ThresholdSignatures(String configPath) throws IOException {
        Path path = Path.of(configPath);
        ObjectMapper mapper = new ObjectMapper();
        this.config = mapper.readValue(path.toFile(), ThresholdNodeConfig.class);
        this.thresholdSig = new ThresholdSigEd25519(config.getThreshold(), config.getTotalReplicas());
        this.privateShare = Scalar.fromBits(Base64.getDecoder().decode(config.getPrivateShare()));
        this.aggregatePublicKey = Base64.getDecoder().decode(config.getAggregatePublicKey());
    }

    /**
     * REPLICAS: Phase 1 of Voting: Each replica creates a nonce commitment for the given QC and stores the  private nonce locally
     * The replica then sends the nonce commitment to the leader, who will aggregate it with the other replicas' commitments.
     * 
     * @return The nonce commitment to be sent to the leader, encoded as a Base64 string
     */
    public String createNonceCommitment(Phase qcType, long viewNumber, String nodeDigest) {
        try{
            String payload = buildQcPayload(qcType.name(), viewNumber, nodeDigest);

            // Generate a nonce and store it locally 
            Scalar nonce = thresholdSig.computeRi(privateShare, payload);
            pendingNonces.put(payload, nonce);

            EdwardsPoint nonceCommitmentPoint = ThresholdSigEd25519.mulBasepoint(nonce);
            return Base64.getEncoder().encodeToString(nonceCommitmentPoint.compress().toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create nonce commitment: " + e.getMessage());
        }
    }

    /**
     * LEADER: Combines multiple nonce commitments into an aggregate commitment that will be used 
     * for the partial signatures in phase 2 of voting.
     * @param nonceCommitments List of nonce commitments from the replicas, indexed by replica ID
     * @return The aggregated nonce commitment to be sent to the replicas, encoded as a Base64 string
     */
    public String aggregateCommitments(Set<String> nonceCommitments) throws InvalidEncodingException {
        if (nonceCommitments == null || nonceCommitments.size() < config.getThreshold()) {
            throw new IllegalArgumentException("Need at least threshold nonce commitments");
        }

        List<EdwardsPoint> points = new ArrayList<>();
        for (String commitment : nonceCommitments) {
            byte[] encodedPoint = Base64.getDecoder().decode(commitment);
            points.add(new CompressedEdwardsY(encodedPoint).decompress());
        }

        EdwardsPoint aggregatedCommitmentPoint = thresholdSig.computeR(points);
        return Base64.getEncoder().encodeToString(aggregatedCommitmentPoint.compress().toByteArray());
    }

    /**
     * REPLICAS: Phase 2 of Voting: Each replica creates a partial signature using its private share and the aggregated commitment from phase 1,
     * and sends it to the leader.
     * @param aggregatedCommitment The aggregated nonce commitment from phase 1, encoded as a Base64 string
     * @param signerIndices The IDs of the nodes who submitted commitments
     * @return The partial signature, encoded as a Base64 string
     */
    public String createPartialSignature(Phase qcType, long viewNumber, String nodeDigest, String aggregatedCommitment, Set<Long> signerIndices) {
        try {
            String payload = buildQcPayload(qcType.name(), viewNumber, nodeDigest);
            Scalar nonce = pendingNonces.get(payload);

            if (nonce == null) {
                throw new IllegalStateException("Missing round-1 nonce for payload " + payload);
            }
            EdwardsPoint aggregatedCommitmentPoint = new CompressedEdwardsY(Base64.getDecoder().decode(aggregatedCommitment)).decompress();
            Scalar challenge = thresholdSig.computeK(aggregatePublicKey, aggregatedCommitmentPoint, payload);
            Set<Integer> signerSet = new java.util.HashSet<>();
            for (Long signerIndex : signerIndices) {
                signerSet.add(Math.toIntExact(signerIndex));
            }

            // Library expects signer set as 0-based replica IDs; replica position stays 1-based below.
            Scalar partial = thresholdSig.computeSignature(config.getReplicaIndex() + 1, privateShare, nonce, challenge, signerSet);
            return Base64.getEncoder().encodeToString(partial.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create partial signature: " + e.getMessage());
        }
    }

    /**
     * LEADER: Combines multiple partial signatures into a valid Quorum Certificate (QC), aggregate signature
     * @param aggregatedNonce The aggregated nonce commitment from the previous step 
     * @param partialSignatures Partial signatures from the signers, replicas (must be at least threshold)
     * @return The quorum certificate, encoded as a Base64 string
     */
    public String createQuorumCertificate(String aggregatedNonce, Set<String> partialSignatures ) {
        if (partialSignatures == null || partialSignatures.size() < config.getThreshold()) {
            throw new IllegalArgumentException("Need at least threshold partial signatures");
        }
        try {
            EdwardsPoint aggregatedNoncePoint = new CompressedEdwardsY(Base64.getDecoder().decode(aggregatedNonce)).decompress();
            List<Scalar> partials = new ArrayList<>();
            for (String partialSignature : partialSignatures) {
                partials.add(Scalar.fromBits(Base64.getDecoder().decode(partialSignature)));
            }

            byte[] signature = thresholdSig.computeSignature(aggregatedNoncePoint, partials);
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create quorum certificate: " + e.getMessage(), e);
        }
    }

    public boolean verifyQuorumCertificate(QuorumCertificateBlock qc){
        if (qc == null || qc.getBlock() == null || qc.getAggregateSignature() == null) {
            return false;
        }
        return verifyAggregateSignature(qc.getQcType(), qc.getViewNumber(), qc.getBlock().getSelfDigest(), qc.getAggregateSignature());
    }

    public boolean verifyAggregateSignature(String qcType, long viewNumber, String nodeDigest, String aggregateSignature){
        String payload = buildQcPayload(qcType, viewNumber, nodeDigest);
        try {
            boolean valid = ThresholdSigEd25519.verify(aggregatePublicKey, Base64.getDecoder().decode(aggregateSignature), payload.getBytes(StandardCharsets.UTF_8));
            return valid;
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify aggregate signature: " + e.getMessage(), e);
        }
    }

    public String buildQcPayload(String qcType, long viewNumber, String nodeDigest) {
        return qcType + "|" + viewNumber + "|" + nodeDigest;
    }
}