package com.depchain.crypto;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.PublicKey;
import java.io.FileInputStream;
import java.security.Signature;
import java.util.Base64;

public class KeyStoreController {
    private KeyStore keystore;
    private final String keystorePassword;

    public KeyStoreController(String keystorePath, String keystorePassword){
        this.keystorePassword = keystorePassword;
        try {
            this.keystore = KeyStore.getInstance("PKCS12");
            FileInputStream fis = new FileInputStream(keystorePath);
            this.keystore.load(fis, this.keystorePassword.toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load keystore", e);
        }
    }

    public PublicKey getPublicKey(String alias) throws Exception {
        Certificate cert = keystore.getCertificate(alias);
        if (cert == null) {
            throw new RuntimeException("No certificate found for alias: " + alias);
        }
        return cert.getPublicKey();
    }

    public PrivateKey getPrivateKey(String alias) throws Exception {
        return (PrivateKey) keystore.getKey(alias, keystorePassword.toCharArray());
    }

    public String sign(String alias, String message) throws Exception {
        PrivateKey privateKey = getPrivateKey(alias);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes());
        byte[] signedBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signedBytes);
    }

    public boolean verifySignature(String alias, String message, String signatureStr) throws Exception {
        PublicKey publicKey = getPublicKey(alias);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(message.getBytes());
        byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
        return signature.verify(signatureBytes);
    }
    
}
