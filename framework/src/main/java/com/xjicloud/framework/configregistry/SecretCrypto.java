package com.xjicloud.framework.configregistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import com.xjicloud.framework.config.FrameworkProperties;

@Component
public class SecretCrypto {

    private final SecretKeySpec keySpec;

    public SecretCrypto(FrameworkProperties properties) {
        String key = properties.encryptionKey();
        if (key == null || key.length() < 16) {
            key = "change-me-aes-key-32bytes-long!!";
        }
        byte[] digest = sha256(key.getBytes(StandardCharsets.UTF_8));
        this.keySpec = new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        if (encrypted.contains("***")) {
            return encrypted;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encrypted;
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
