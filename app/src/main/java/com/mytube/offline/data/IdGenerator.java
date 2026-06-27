package com.mytube.offline.data;

import java.security.SecureRandom;

/**
 * IdGenerator — mirrors PHP generate_video_id() from the original script.
 *
 * The PHP version does:
 *   substr(str_shuffle(str_repeat('0-9a-zA-Z', ceil(11/62))), 1, 11)
 *
 * In Java we do the equivalent: pick 11 random chars from the 62-char
 * alphabet. SecureRandom is used instead of Math.random() so video ids
 * are harder to guess for anyone scanning the local DB.
 */
public final class IdGenerator {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int LEN = 11;
    private static final SecureRandom RNG = new SecureRandom();

    private IdGenerator() {}

    public static String nextVideoId() {
        StringBuilder sb = new StringBuilder(LEN);
        for (int i = 0; i < LEN; i++) {
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
