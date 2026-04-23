package com.depchain.state;

import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.fluent.SimpleAccount;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.apache.tuweni.units.bigints.UInt256;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;


public class WorldStateHash {

    public static String computeWorldStateHash(SimpleWorld world) {
        try {
            Map<String, String> canonical = new TreeMap<>();
            canonical.put("format", "depchain-world-v1");
            canonical.put("evm_root", computeEvmStateHash(world));
            return sha256Base64(canonical.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed world state hash", e);
        }
    }

    private static String computeEvmStateHash(SimpleWorld world) {
        Map<String, String> canonical = new TreeMap<>();

        for (Account account : world.getTouchedAccounts()) {
            String addressHex = account.getAddress().toHexString().toLowerCase();
            canonical.put("acct:" + addressHex + ":nonce", Long.toString(account.getNonce()));
            canonical.put("acct:" + addressHex + ":balance", account.getBalance().toString());
            canonical.put("acct:" + addressHex + ":code_hash", account.getCodeHash().toHexString());

            if (account instanceof SimpleAccount simpleAccount) {
                for (Map.Entry<UInt256, UInt256> slot : simpleAccount.getUpdatedStorage().entrySet()) {
                    canonical.put(
                        "acct:" + addressHex + ":storage:" + slot.getKey().toHexString(),
                        slot.getValue().toHexString()
                    );
                }
            }
        }

        for (var deletedAddress : world.getDeletedAccountAddresses()) {
            canonical.put("deleted:" + deletedAddress.toHexString().toLowerCase(), "1");
        }

        return sha256Base64(canonical.toString());
    }

    public static void writeWorldStateToFile(SimpleWorld world, long viewNumber) {
        try {
            File worldstateDir = new File("depchain/worldstatehash");
            if (!worldstateDir.exists()) {
                worldstateDir.mkdirs();
            }

            // Build world state JSON
            Map<String, Object> worldStateData = new HashMap<>();
            
            for (Account account : world.getTouchedAccounts()) {
                String addressHex = account.getAddress().toHexString();
                Map<String, Object> accountData = new HashMap<>();
                accountData.put("nonce", account.getNonce());
                accountData.put("balance", account.getBalance().toString());
                accountData.put("codeHash", account.getCodeHash().toHexString());
                
                if (account instanceof SimpleAccount simpleAccount) {
                    Map<String, String> storage = new HashMap<>();
                    for (Map.Entry<UInt256, UInt256> slot : simpleAccount.getUpdatedStorage().entrySet()) {
                        storage.put(slot.getKey().toHexString(), slot.getValue().toHexString());
                    }
                    if (!storage.isEmpty()) {
                        accountData.put("storage", storage);
                    }
                }
                
                worldStateData.put(addressHex, accountData);
            }
            
            // Write to JSON file
            ObjectMapper objectMapper = new ObjectMapper();
            String filename = "depchain/worldstatehash/" + viewNumber + ".json";
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filename), worldStateData);

        } catch (Exception e) {
            System.err.println("[WorldState] Failed to write world state to JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String sha256Base64(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed hashing world state snapshot", e);
        }
    }
}
