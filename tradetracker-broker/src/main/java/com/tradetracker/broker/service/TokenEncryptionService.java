package com.tradetracker.broker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for OAuth tokens stored in the database.
 *
 * Each token gets its own random IV so identical tokens produce different ciphertext.
 * The encryption key is derived from INTERNAL_JWT_SECRET via PBKDF2 — never stored in DB.
 *
 * Format of stored value: Base64(IV[12] + Ciphertext + AuthTag[16])
 */
@Service
public class TokenEncryptionService {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int IV_LENGTH     = 12;      // 96-bit IV for GCM
    private static final int TAG_LENGTH    = 128;     // 128-bit auth tag
    private static final int KEY_LENGTH    = 256;
    private static final int ITERATIONS    = 310_000; // OWASP 2023 recommendation
    private static final byte[] SALT       = "TradeTrackerTokenSalt2024".getBytes();

    private final SecretKeySpec secretKey;
    private final SecureRandom  random = new SecureRandom();

    public TokenEncryptionService(
            @Value("${INTERNAL_JWT_SECRET:change_me_in_production_use_32_char_minimum}") String secret) {
        this.secretKey = deriveKey(secret);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv         = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Prepend IV to ciphertext
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv,        0, combined, 0,         IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return "";
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv       = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];

            System.arraycopy(combined, 0,         iv,         0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH,  ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt token", e);
        }
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                secret.toCharArray(), SALT, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive encryption key", e);
        }
    }
}
