package com.example.tvvoiceprobe;

/**
 * One microphone owner at a time. Nothing here expires on a timer.
 * A user action does not become the owner while the wake listener still
 * has an open AudioRecord. The wake thread publishes that release, and
 * only then does the waiting owner hold the lease.
 */
public final class MicLease {
    public static final String WAKE = "wake";
    public static final String OVERLAY = "overlay";
    public static final String TALK = "talk";
    public static final String DIAGNOSTICS = "diagnostics";

    private static String holder;
    private static String waiting;
    private static boolean wakeRecorderOpen;

    private MicLease() { }

    public static synchronized boolean acquire(String owner) {
        if (owner == null || owner.isEmpty()) return false;
        if (holder != null && !holder.equals(owner) && !WAKE.equals(holder)) return false;
        // Do not transfer an idle WAKE lease immediately: the wake thread
        // may have acquired WAKE and be about to create its AudioRecord.
        // It must explicitly release/hand off, even before startRecording().
        if (!WAKE.equals(owner) && (WAKE.equals(holder) || wakeRecorderOpen)) {
            // One pending handoff at a time. Never silently overwrite TALK
            // with a later diagnostics/overlay caller.
            if (waiting != null && !waiting.equals(owner)) return false;
            waiting = owner;
            MicLease.class.notifyAll();
            return false;
        }
        if (WAKE.equals(owner) && waiting != null) return false;
        holder = owner;
        if (!WAKE.equals(owner)) waiting = null;
        return true;
    }

    /** Wake calls this after AudioRecord.startRecording has succeeded. */
    public static synchronized boolean wakeRecorderOpened() {
        // Wake must have the lease BEFORE opening any AudioRecord.
        // A competing owner may win during a cancellation: never claim
        // the recorder is protected in that case.
        if (!WAKE.equals(holder) || wakeRecorderOpen) return false;
        wakeRecorderOpen = true;
        return true;
    }

    public static synchronized boolean wakeShouldStop() {
        return waiting != null;
    }

    /**
     * Wake calls this only after AudioRecord.release has returned.
     * A waiting TALK, overlay, or diagnostics owner becomes the holder here.
     */
    public static synchronized void wakeRecorderReleased() {
        wakeRecorderOpen = false;
        if (WAKE.equals(holder)) holder = null;
        if (waiting != null) {
            holder = waiting;
            waiting = null;
        }
        MicLease.class.notifyAll();
    }

    public static boolean awaitHeld(String owner, long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        synchronized (MicLease.class) {
            while (!owner.equals(holder) || wakeRecorderOpen) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return owner.equals(holder) && !wakeRecorderOpen;
                try {
                    MicLease.class.wait(left);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    public static synchronized boolean isWaiting(String owner) {
        return owner != null && owner.equals(waiting);
    }

    public static synchronized void release(String owner) {
        if (owner != null && owner.equals(waiting)) waiting = null;
        if (owner != null && owner.equals(holder) && !wakeRecorderOpen) {
            holder = WAKE.equals(owner) && waiting != null ? waiting : null;
            if (WAKE.equals(owner)) waiting = null;
        }
        MicLease.class.notifyAll();
    }

    public static synchronized boolean heldByOther(String owner) {
        return holder != null && (owner == null || !owner.equals(holder));
    }

    public static synchronized String holder() {
        return holder;
    }

    static synchronized void reset() {
        holder = null;
        waiting = null;
        wakeRecorderOpen = false;
        MicLease.class.notifyAll();
    }
}
