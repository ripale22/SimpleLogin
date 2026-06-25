package com.premiumauth.simplelogin.utils;

import org.mindrot.jbcrypt.BCrypt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class to handle password hashing and verification in a hybrid manner.
 * Supports standard BCrypt as well as legacy AuthMe (SHA256) hashes.
 */
public class PasswordHasher {

    public enum VerificationResult {
        SUCCESS_UP_TO_DATE,
        SUCCESS_NEEDS_REHASH,
        FAILED
    }

    /**
     * Hashes a password using the preferred algorithm (BCrypt with work factor 12).
     */
    public static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    /**
     * Verifies a password against a stored hash (either BCrypt or AuthMe SHA256).
     */
    public static VerificationResult verify(String password, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) {
            return VerificationResult.FAILED;
        }

        // AuthMe SHA256 format
        if (storedHash.startsWith("$SHA$")) {
            if (verifyAuthMeSHA256(password, storedHash)) {
                return VerificationResult.SUCCESS_NEEDS_REHASH;
            }
            return VerificationResult.FAILED;
        }

        // Standard BCrypt check
        try {
            if (BCrypt.checkpw(password, storedHash)) {
                return VerificationResult.SUCCESS_UP_TO_DATE;
            }
        } catch (Exception e) {
            return VerificationResult.FAILED;
        }

        return VerificationResult.FAILED;
    }

    /**
     * Verifies a password using the AuthMe SHA256 scheme: $SHA$salt$hash.
     * Formula: sha256(sha256(password) + salt)
     */
    private static boolean verifyAuthMeSHA256(String password, String storedHash) {
        String[] parts = storedHash.split("\\$");
        // parts[0] is empty due to the leading $, parts[1] is "SHA", parts[2] is salt, parts[3] is hash
        if (parts.length < 4) {
            return false;
        }
        String salt = parts[2];
        String expectedHash = parts[3];
        String calculatedHash = sha256(sha256(password) + salt);
        return calculatedHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * Generates a SHA-256 hash in hexadecimal format.
     */
    private static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
