package com.depchain.crypto;

import javax.crypto.KeyAgreement;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class KeyExchange {
    private static final String HMAC_ALG = "HmacSHA256";

    public KeyExchange() {
    }


    public static byte[] sharedSecret(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(privateKey);
        ka.doPhase(publicKey, true);
        return ka.generateSecret();
    }

    /**
     * Derive an HMAC key from the shared secret and message.
     * @param sharedSecret The shared secret derived from the key agreement protocol.
     * @param message The message for which to create an HMAC.
     * @return The HMAC message authentication code as a byte array.
     * @throws Exception
     */
    public static byte[] createHmacKey(byte[] sharedSecret, String message) throws Exception {
        // Create HMAC from the shared secret
        SecretKeySpec keySpec = new SecretKeySpec(sharedSecret, HMAC_ALG);
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(keySpec);
        return mac.doFinal(message.getBytes());
    }

    
}
