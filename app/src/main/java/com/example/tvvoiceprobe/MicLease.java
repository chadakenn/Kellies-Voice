package com.example.tvvoiceprobe;

/**
 * One microphone owner at a time. A user action may take the microphone
 * from the wake listener. The wake listener cannot take it from anyone else,
 * and nothing here expires on a timer.
 */
public final class MicLease {
    public static final String WAKE = "wake";
    public static final String OVERLAY = "overlay";
    public static final String TALK = "talk";
    public static final String DIAGNOSTICS = "diagnostics";

    private static String holder;

    private MicLease() { }

    public static synchronized boolean acquire(String owner) {
        if (owner == null || owner.isEmpty()) return false;
        if (holder == null || holder.equals(owner) || WAKE.equals(holder)) {
            holder = owner;
            return true;
        }
        return false;
    }

    public static synchronized void release(String owner) {
        if (owner != null && owner.equals(holder)) holder = null;
    }

    public static synchronized boolean heldByOther(String owner) {
        return holder != null && (owner == null || !owner.equals(holder));
    }

    public static synchronized String holder() {
        return holder;
    }

    static synchronized void reset() {
        holder = null;
    }
}
