package com.kanban.util;

public final class Rank {

    private static final long STEP = 1000;
    private static final long MAX = 9_999_999L;

    private Rank() {
    }

    /** Position after the given last position (null = empty column). */
    public static String after(String last) {
        long base = last == null ? 0 : value(last);
        return format(base + STEP);
    }

    public static long value(String position) {
        try {
            return Long.parseLong(position);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid position: " + position);
        }
    }

    public static String format(long n) {
        if (n < 0 || n > MAX) {
            throw new IllegalStateException("Position out of range: " + n);
        }
        return String.format("%07d", n);
    }
}