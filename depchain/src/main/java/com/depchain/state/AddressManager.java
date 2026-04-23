package com.depchain.state;

import org.hyperledger.besu.datatypes.Address;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

public class AddressManager {
    private static final Pattern CLIENT_ALIAS = Pattern.compile("^client\\d+$");

    //Parses a client address
    public static Address parseAddress(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("Address is blank");
        }

        String value = raw.trim();
        if (value.startsWith("0x")) {
            return Address.fromHexString(value);
        }

        if (!CLIENT_ALIAS.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsupported address format (expected clientX or 0x...): " + value);
        }

        return deriveAddressFromAlias(value);
    }

    // Turns a normal address replicaX or clientX, into the formmat expected by the accounts in worldState
    public static Address deriveAddressFromAlias(String alias) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(alias.getBytes(StandardCharsets.UTF_8));

            byte[] addressBytes = new byte[20];
            System.arraycopy(hash, hash.length - 20, addressBytes, 0, 20);

            StringBuilder sb = new StringBuilder("0x");
            for (byte b : addressBytes) {
                sb.append(String.format("%02x", b));
            }
            return Address.fromHexString(sb.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to derive address from alias: " + alias, e);
        }
    }

    //Derives the contract address, this time using a nonce
    public static Address deriveContractAddress(Address sender, Long nonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = sender.toHexString() + ":" + (nonce == null ? 0L : nonce);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            byte[] addressBytes = new byte[20];
            System.arraycopy(hash, hash.length - 20, addressBytes, 0, 20);

            StringBuilder sb = new StringBuilder("0x");
            for (byte b : addressBytes) {
                sb.append(String.format("%02x", b));
            }
            return Address.fromHexString(sb.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to derive contract address", e);
        }
    }
    
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
