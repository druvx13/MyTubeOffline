package com.mytube.offline.util;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PasswordUtil — the Android equivalent of PHP's password_hash() / password_verify().
 *
 * PHP uses bcrypt by default. Android ships no built-in bcrypt, and bundling a
 * third-party BCrypt source file would bloat the AIDE Pro project. PBKDF2WithHmacSHA1
 * is built into javax.crypto and is widely accepted as a secure password hash.
 *
 * Because this app is 100% offline (no PHP<->Android password exchange ever
 * happens), we don't need bcrypt-compatibility — we only need to verify passwords
 * against a hash that this same app produced.
 *
 * Stored format:  pbkdf2:<iterations>:<saltHex>:<hashHex>
 */
public final class PasswordUtil {

    private static final int ITERATIONS = 12_000;
    private static final int KEY_LEN    = 256; // bits
    private static final int SALT_LEN   = 16;  // bytes
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordUtil() {}

    public static String hash(String password) {
        byte[] salt = new byte[SALT_LEN];
        RNG.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return "pbkdf2:" + ITERATIONS + ":" + hex(salt) + ":" + hex(hash);
    }

    public static boolean verify(String password, String stored) {
        if (stored == null) return false;
        String[] parts = stored.split(":");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
        try {
            int    iterations = Integer.parseInt(parts[1]);
            byte[] salt       = fromHex(parts[2]);
            byte[] hash       = fromHex(parts[3]);
            byte[] candidate  = pbkdf2(password, salt, iterations);
            return constantTimeEquals(hash, candidate);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LEN);
            return f.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // Should never happen on Android — these algorithms are guaranteed by the platform.
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    private static byte[] fromHex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** Compares two byte arrays in constant time to avoid timing attacks. */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
