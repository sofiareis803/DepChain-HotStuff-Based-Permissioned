package com.depchain.blockchain.model;

import com.depchain.blockchain.model.Account;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.nio.charset.StandardCharsets;


public class ContractAccount extends Account {
    private byte[] code; // Bytes of the smart contract code
    private Map<String, String> storage; // Key-value pairs representing the contract's storage

    public ContractAccount(String address, long balance, byte[] code, Map<String, String> storage) {
        super(address, balance);
        this.code = code;
        this.storage = storage;
    }

    public String getCADigest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();

            // Hash the code
            if (code != null) {
                byte[] codeHashBytes = digest.digest(code);
                sb.append(Base64.getEncoder().encodeToString(codeHashBytes));
            }
            sb.append(":");

            // Hash the storage
            if (storage != null) {
                List<String> sortedKeys = new ArrayList<>(storage.keySet());
                Collections.sort(sortedKeys);
                for (String key : sortedKeys) {
                    sb.append(key).append(":").append(storage.get(key)).append(":");
                }
            }

            byte[] finalHashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(finalHashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public byte[] getCode() {
        return code;
    }

    public Map<String, String> getStorage() {
        return storage;
    }
}