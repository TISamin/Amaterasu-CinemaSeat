package com.cinemaseat.booking;

import java.security.SecureRandom;

/**
 * Short, human-readable booking reference. Not security-sensitive for the hackathon —
 * uniqueness is enforced by DB UNIQUE(booking_ref) and retried on collision.
 */
public final class BookingRefGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();

    private BookingRefGenerator() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder("BK-");
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}