package com.depchain.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weavechain.curve25519.Scalar;
import com.weavechain.sig.ThresholdSigEd25519;
import com.weavechain.sig.ThresholdSigEd25519Params;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class ThresholdSignaturesGenerator {
    private static final String THRESHOLD_DIR = "../security/threshold";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Use: java ThresholdSignaturesGenerator <number_of_nodes>");
            System.exit(1);
        }

        int n = Integer.parseInt(args[0]); // Total number of replicas
        int f = (n - 1) / 3;                // f = (n - 1) / 3
        int t = 2 * f + 1;                  // threshold

        Path outputDir = Path.of(THRESHOLD_DIR);
        Files.createDirectories(outputDir);

        ThresholdSigEd25519 tsig = new ThresholdSigEd25519(t, n);
        ThresholdSigEd25519Params params = tsig.generate();
        ObjectMapper mapper = new ObjectMapper();
        String encodedPublicKey = Base64.getEncoder().encodeToString(params.getPublicKey());

        for (int index = 0; index < n; index++) {
            Scalar privateShare = params.getPrivateShares().get(index);

            ThresholdNodeConfig nodeConfig = new ThresholdNodeConfig();
            nodeConfig.setReplicaIndex(index);
            nodeConfig.setThreshold(t);
            nodeConfig.setTotalReplicas(n);
            nodeConfig.setAggregatePublicKey(encodedPublicKey);
            nodeConfig.setPrivateShare(Base64.getEncoder().encodeToString(privateShare.toByteArray()));

            Path outputFile = outputDir.resolve("node" + (index + 1) + "-threshold.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), nodeConfig);
        }
    }
}