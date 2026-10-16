package com.neuroforge.nexus.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EncryptionUtil}.
 *
 * No Spring context is loaded — the secret field is injected via ReflectionTestUtils
 * to keep the tests fast and self-contained.
 */
class EncryptionUtilTest {

    private static final String TEST_SECRET =
            "test-secret-that-is-long-enough-for-validation-purposes";

    private EncryptionUtil util;

    @BeforeEach
    void setUp() {
        util = new EncryptionUtil();
        ReflectionTestUtils.setField(util, "secret", TEST_SECRET);
        util.validateSecret(); // mirrors @PostConstruct behaviour
    }

    // ── GCM round-trip ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GCM: encrypt then decrypt returns original plaintext")
    void gcmRoundTrip() {
        String plaintext  = "gho_github_access_token_example";
        String ciphertext = util.encrypt(plaintext);

        assertNotNull(ciphertext, "encrypt must not return null");
        assertTrue(ciphertext.startsWith(EncryptionUtil.V2_PREFIX),
                "encrypted value must start with v2: prefix");

        String decrypted = util.decrypt(ciphertext);
        assertEquals(plaintext, decrypted, "decrypted value must equal original plaintext");
    }

    @Test
    @DisplayName("GCM: encrypting same plaintext twice produces different ciphertexts (unique IVs)")
    void gcmProducesDifferentCiphertextsForSameInput() {
        String plaintext = "same_token_value";
        String first  = util.encrypt(plaintext);
        String second = util.encrypt(plaintext);

        assertNotEquals(first, second,
                "Two encryptions of the same value must produce different ciphertexts due to random IVs");

        // Both must still decrypt correctly
        assertEquals(plaintext, util.decrypt(first));
        assertEquals(plaintext, util.decrypt(second));
    }

    @Test
    @DisplayName("GCM: tampered ciphertext (flipped bit in payload) is rejected")
    void gcmTamperedCiphertextRejected() {
        String ciphertext = util.encrypt("token_to_tamper");
        // Strip the v2: prefix, decode, flip a byte in the ciphertext area, re-encode
        String base64Payload = ciphertext.substring(EncryptionUtil.V2_PREFIX.length());
        byte[] raw = Base64.getDecoder().decode(base64Payload);

        // Flip a bit in the ciphertext region (after the 12-byte IV)
        raw[15] ^= 0xFF;

        String tampered = EncryptionUtil.V2_PREFIX + Base64.getEncoder().encodeToString(raw);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> util.decrypt(tampered),
                "GCM authentication must reject a tampered ciphertext");

        // Confirm it's a crypto failure, not something else
        assertNotNull(ex.getCause(), "cause should be present");
    }

    @Test
    @DisplayName("GCM: tampered IV (flipped bit in IV area) is rejected")
    void gcmTamperedIvRejected() {
        String ciphertext = util.encrypt("token_to_tamper_iv");
        String base64Payload = ciphertext.substring(EncryptionUtil.V2_PREFIX.length());
        byte[] raw = Base64.getDecoder().decode(base64Payload);

        // Flip a bit in the IV region (bytes 0-11)
        raw[0] ^= 0x01;

        String tampered = EncryptionUtil.V2_PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThrows(RuntimeException.class,
                () -> util.decrypt(tampered),
                "GCM authentication must reject a message with a tampered IV");
    }

    // ── Legacy ECB migration path ────────────────────────────────────────────

    @Test
    @DisplayName("Legacy ECB: decrypt transparently handles unprefixed ECB ciphertext")
    void legacyEcbDecryptionWorks() throws Exception {
        // Produce a real ECB-encrypted value using the same key derivation as the old code.
        // This simulates a token stored in the database before the v2 migration.
        String plaintext       = "legacy_github_token_abc123";
        String ecbCiphertext   = encryptWithLegacyEcb(plaintext);

        assertFalse(ecbCiphertext.startsWith(EncryptionUtil.V2_PREFIX),
                "Legacy ciphertext must not have the v2: prefix");

        String decrypted = util.decrypt(ecbCiphertext);
        assertEquals(plaintext, decrypted,
                "Legacy ECB ciphertext must decrypt transparently to the original value");
    }

    @Test
    @DisplayName("isLegacyFormat: correctly identifies ECB vs GCM values")
    void isLegacyFormatDetection() throws Exception {
        String ecbCiphertext = encryptWithLegacyEcb("some_token");
        String gcmCiphertext = util.encrypt("some_token");

        assertTrue(util.isLegacyFormat(ecbCiphertext),
                "Unprefixed ECB value must be identified as legacy");
        assertFalse(util.isLegacyFormat(gcmCiphertext),
                "v2: prefixed GCM value must not be identified as legacy");
        assertFalse(util.isLegacyFormat(null),
                "null must not be identified as legacy");
    }

    // ── Null / edge cases ────────────────────────────────────────────────────

    @Test
    @DisplayName("encrypt(null) returns null")
    void encryptNullReturnsNull() {
        assertNull(util.encrypt(null));
    }

    @Test
    @DisplayName("decrypt(null) returns null")
    void decryptNullReturnsNull() {
        assertNull(util.decrypt(null));
    }

    @Test
    @DisplayName("encrypt preserves empty string round-trip")
    void encryptEmptyString() {
        String ciphertext = util.encrypt("");
        assertEquals("", util.decrypt(ciphertext));
    }

    // ── Key validation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("validateSecret: throws on null secret")
    void validateSecretFailsOnNull() {
        EncryptionUtil badUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(badUtil, "secret", null);

        assertThrows(IllegalStateException.class, badUtil::validateSecret,
                "null secret must cause a startup failure");
    }

    @Test
    @DisplayName("validateSecret: throws on blank secret")
    void validateSecretFailsOnBlank() {
        EncryptionUtil badUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(badUtil, "secret", "   ");

        assertThrows(IllegalStateException.class, badUtil::validateSecret,
                "blank secret must cause a startup failure");
    }

    @Test
    @DisplayName("validateSecret: throws on secret shorter than minimum length")
    void validateSecretFailsOnShortSecret() {
        EncryptionUtil badUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(badUtil, "secret", "tooshort");

        assertThrows(IllegalStateException.class, badUtil::validateSecret,
                "secret shorter than 16 chars must cause a startup failure");
    }

    @Test
    @DisplayName("validateSecret: accepts a properly configured secret")
    void validateSecretPassesOnGoodSecret() {
        EncryptionUtil goodUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(goodUtil, "secret", "a-sufficiently-long-secret-value");

        assertDoesNotThrow(goodUtil::validateSecret,
                "a valid secret must not throw");
    }

    // ── Helper: simulate legacy ECB encryption (the old algorithm) ───────────

    /**
     * Reproduces exactly what the old {@code EncryptionUtil.encrypt()} did —
     * AES/ECB/PKCS5Padding — so tests can generate realistic legacy ciphertexts
     * without depending on the old code being present.
     */
    private String encryptWithLegacyEcb(String plainText) throws Exception {
        java.security.MessageDigest sha =
                java.security.MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(TEST_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        javax.crypto.spec.SecretKeySpec key =
                new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(
                plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }
}
