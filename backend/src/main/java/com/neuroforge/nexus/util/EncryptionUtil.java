package com.neuroforge.nexus.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256 encryption for GitHub access tokens stored at rest.
 *
 * <p>Ciphertext format versions:
 * <ul>
 *   <li><b>Legacy / ECB (no prefix)</b> — produced before the v2 migration.
 *       Decrypted transparently; never written by this class anymore.</li>
 *   <li><b>v2 / GCM (prefix "v2:")</b> — current format.
 *       Layout: {@code Base64( IV[12 bytes] | ciphertext | GCM auth tag[16 bytes] )}.
 *       Provides confidentiality + authenticated integrity (tamper detection).</li>
 * </ul>
 *
 * <p>Migration path: existing ECB-encrypted rows are decrypted by the legacy path
 * and remain in the database until the user reconnects their GitHub account, at which
 * point {@link com.neuroforge.nexus.oauth.GitHubOAuthService} calls {@link #encrypt}
 * and persists the v2 ciphertext. No automatic bulk re-encryption is performed —
 * lazy migration on OAuth reconnect is safe and avoids unintended bulk DB writes.
 */
@Component
public class EncryptionUtil {

    private static final Logger log = LoggerFactory.getLogger(EncryptionUtil.class);

    static final String V2_PREFIX = "v2:";
    private static final int GCM_IV_BYTES  = 12;
    private static final int GCM_TAG_BITS  = 128;
    // AES-256 requires a 32-byte key; SHA-256 always produces exactly 32 bytes.
    private static final int REQUIRED_KEY_BYTES = 32;

    @Value("${app.encryption.secret}")
    private String secret;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Validates the configured encryption secret at application startup.
     * Fails fast with a clear message rather than silently accepting a weak key.
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "app.encryption.secret (APP_ENCRYPTION_SECRET) is not configured. " +
                "Set it to a strong random string of at least 32 characters. " +
                "Generate one with: openssl rand -hex 32");
        }
        // SHA-256 of the secret produces the 32-byte AES key; the secret itself
        // only needs to have enough entropy. Enforce a minimum length as a
        // basic sanity check — a 8-char secret is trivially brute-forceable.
        if (secret.length() < 16) {
            throw new IllegalStateException(
                "APP_ENCRYPTION_SECRET is too short (minimum 16 characters). " +
                "Generate a strong value with: openssl rand -hex 32");
        }
        log.info("EncryptionUtil: encryption secret configured and validated");
    }

    /**
     * Encrypts {@code plainText} using AES-256-GCM with a fresh random IV.
     * Returns a string prefixed with {@value #V2_PREFIX}.
     * Never logs the plaintext or the derived key.
     *
     * @param plainText value to encrypt; {@code null} returns {@code null}
     * @return v2-prefixed Base64 ciphertext, or {@code null} if input is {@code null}
     * @throws RuntimeException wrapping the underlying crypto exception on failure
     */
    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Layout: IV (12 bytes) || ciphertext+tag
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,          iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length,  ciphertext.length);

            return V2_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypts a stored token. Handles both legacy ECB (no prefix) and
     * current GCM ({@value #V2_PREFIX} prefix) formats transparently.
     * Never logs the decrypted value or the key.
     *
     * @param encryptedText stored ciphertext; {@code null} returns {@code null}
     * @return decrypted plaintext
     * @throws RuntimeException wrapping the underlying crypto exception on failure
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null) return null;
        try {
            if (encryptedText.startsWith(V2_PREFIX)) {
                return decryptGcm(encryptedText.substring(V2_PREFIX.length()));
            } else {
                return decryptLegacyEcb(encryptedText);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    /**
     * Returns {@code true} if the stored value uses the legacy ECB format
     * (no {@value #V2_PREFIX} prefix), meaning it was written before the v2
     * migration and will be upgraded to GCM the next time the user reconnects
     * their GitHub account.
     */
    public boolean isLegacyFormat(String encryptedText) {
        return encryptedText != null && !encryptedText.startsWith(V2_PREFIX);
    }

    // ── private helpers ────────────────────────────────────────────────────

    /**
     * Derives the 32-byte AES-256 key from the configured secret using SHA-256.
     * The secret is hashed rather than used directly so any string length is
     * accepted and always produces a valid 256-bit key.
     */
    private SecretKey deriveKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
        // Defensive check — SHA-256 always returns 32 bytes, but verify explicitly.
        if (keyBytes.length != REQUIRED_KEY_BYTES) {
            throw new IllegalStateException(
                "Key derivation produced unexpected length: " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String decryptGcm(String base64Payload) throws Exception {
        byte[] combined  = Base64.getDecoder().decode(base64Payload);
        if (combined.length < GCM_IV_BYTES + 1) {
            throw new IllegalArgumentException(
                "GCM ciphertext too short to contain a valid IV and payload");
        }
        byte[] iv         = Arrays.copyOfRange(combined, 0,           GCM_IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_BYTES, combined.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * Legacy ECB path — read-only. Kept only for migrating tokens encrypted
     * before the v2 format was introduced. No new ciphertext is ever produced
     * with this algorithm.
     */
    private String decryptLegacyEcb(String base64Ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey());
        byte[] decoded   = Base64.getDecoder().decode(base64Ciphertext);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
