package com.orthodoxicons.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Small cryptographic-hash helper used for image de-duplication and for
 * deriving stable icon UUIDs from a provider id + source URL.
 */
public final class Hashing {

    private Hashing() {
    }

    /**
     * Computes the hex-encoded SHA-256 of the given bytes.
     *
     * @param data input bytes
     * @return lowercase hex digest
     */
    public static String sha256(byte[] data) {
        MessageDigest digest = newDigest();
        byte[] out = digest.digest(data);
        return toHex(out);
    }

    /**
     * Computes the hex-encoded SHA-256 of a string (UTF-8).
     *
     * @param text input text
     * @return lowercase hex digest
     */
    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Derives a stable, deterministic UUID for an icon from its provider id and
     * source URL, so the same remote icon always maps to the same UUID.
     *
     * @param providerId provider identifier
     * @param sourceKey  a stable key (usually the source or image URL)
     * @return a name-based UUID
     */
    public static UUID stableId(String providerId, String sourceKey) {
        String seed = providerId + "|" + sourceKey;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
