package com.example.tvvoiceprobe;

/**
 * Decides who receives a single "authenticated" callback when a standby
 * Home Assistant socket is later adopted by the voice overlay.
 * The standby listener must not start Assist. The overlay must not miss
 * the callback, and it must not receive it twice.
 */
public final class AuthReady {
    private boolean authenticated;
    private boolean delivered;

    /** A dropped socket starts the decision over. */
    public synchronized void reset() {
        authenticated = false;
        delivered = false;
    }

    /**
     * Authentication just succeeded.
     *
     * @param listenerIsOwner false for the idle wake socket, true once a
     *                        real voice screen owns the client
     * @return true only when that listener should run its ready callback now
     */
    public synchronized boolean onAuthenticated(boolean listenerIsOwner) {
        authenticated = true;
        if (delivered || !listenerIsOwner) return false;
        delivered = true;
        return true;
    }

    /**
     * The voice overlay replaced the idle listener.
     *
     * @return true when authentication already happened and this overlay
     *         still needs the one ready callback
     */
    public synchronized boolean ownerAttached() {
        if (!authenticated || delivered) return false;
        delivered = true;
        return true;
    }
}
