package com.example.tvvoiceprobe;

/**
 * Releases a preview microphone once, and does it before the stopped callback.
 * A requested handoff keeps the recorder for the Assist turn.
 */
public final class PreviewStop {
    private final Runnable releaseAction;
    private boolean handoff;
    private boolean released;

    public PreviewStop(Runnable releaseAction) {
        this.releaseAction = releaseAction;
    }

    public synchronized void requestHandoff() {
        handoff = true;
    }

    public synchronized boolean handoffPending() {
        return handoff && !released;
    }

    public synchronized boolean released() {
        return released;
    }

    /** Mark the recorder as taken by Assist. This does not release it. */
    public synchronized void transferred() {
        released = true;
    }

    public void fail(boolean alreadyStopped, Runnable onMiss) {
        boolean notify;
        synchronized (this) {
            notify = !handoff && !alreadyStopped;
            if (!handoff) releaseLocked();
        }
        if (notify && onMiss != null) onMiss.run();
    }

    public synchronized void abandon() {
        handoff = false;
        releaseLocked();
    }

    public synchronized void releaseNow() {
        if (handoff && !released) return;
        releaseLocked();
    }

    private void releaseLocked() {
        if (released) return;
        released = true;
        releaseAction.run();
    }
}
