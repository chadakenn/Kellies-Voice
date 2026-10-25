package com.example.tvvoiceprobe;

/**
 * One lock for starting, sending, stopping, and cleaning up a single Assist run.
 * Callers must not hold this lock across a follow-up handoff or a blocking read.
 */
public final class RunGate {
    private int run = -1;
    private int handler = -1;
    private boolean accepting;
    private boolean recording;
    private boolean stopped;
    private boolean ended;
    private boolean focusHeld;

    public synchronized void open(int runId) {
        run = runId;
        handler = -1;
        accepting = false;
        recording = false;
        stopped = false;
        ended = false;
        focusHeld = false;
    }

    public synchronized boolean assignHandler(int runId, int handlerId) {
        return assignHandler(runId, handlerId, null);
    }

    public synchronized boolean assignHandler(int runId, int handlerId, Runnable mark) {
        if (run != runId || handlerId < 0 || handlerId > 255) return false;
        handler = handlerId;
        if (mark != null) mark.run();
        return true;
    }

    public synchronized void acquire(int runId, Runnable acquireFocus) {
        if (run != runId) return;
        acquireFocus.run();
        focusHeld = true;
    }

    public synchronized boolean arm(int runId, int handlerId) {
        return arm(runId, handlerId, null);
    }

    /** Allow microphone startup for this run. A stop that already won stays won. */
    public synchronized boolean arm(int runId, int handlerId, Runnable mark) {
        if (!same(runId, handlerId) || stopped) return false;
        accepting = true;
        if (mark != null) mark.run();
        return true;
    }

    public synchronized boolean stopAudio(int runId) {
        return stopAudio(runId, null);
    }

    public synchronized boolean stopAudio(int runId, Runnable mark) {
        if (run != runId) return false;
        accepting = false;
        recording = false;
        stopped = true;
        if (mark != null) mark.run();
        return true;
    }

    /** Drop this run. The focus callback runs only when this run still holds focus. */
    public synchronized void cancel(int runId, Runnable releaseFocus) {
        if (run != runId) return;
        run = -1;
        handler = -1;
        accepting = false;
        recording = false;
        stopped = true;
        ended = true;
        if (focusHeld) {
            focusHeld = false;
            releaseFocus.run();
        }
    }

    /**
     * Validate and start under this lock. Returns false when cancellation won,
     * in which case the caller still owns any recorder and must release it.
     */
    public synchronized boolean start(int runId, int handlerId, Runnable startRecording) {
        if (!same(runId, handlerId) || !accepting || stopped) return false;
        startRecording.run();
        if (!same(runId, handlerId) || !accepting || stopped) return false;
        recording = true;
        return true;
    }

    public synchronized boolean send(int runId, int handlerId, Runnable sendPacket) {
        if (!same(runId, handlerId) || !recording) return false;
        sendPacket.run();
        return same(runId, handlerId) && recording;
    }

    public synchronized boolean endAudio(int runId, int handlerId, Runnable sendEnd) {
        if (!same(runId, handlerId) || ended) return false;
        ended = true;
        sendEnd.run();
        return true;
    }

    /** Clear recording and focus only when this run and handler are still current. */
    public synchronized void releaseFocusIfOwner(int runId, int handlerId, Runnable releaseFocus) {
        if (!same(runId, handlerId) || !focusHeld) return;
        accepting = false;
        recording = false;
        focusHeld = false;
        releaseFocus.run();
    }

    public synchronized boolean isRecording(int runId, int handlerId) {
        return same(runId, handlerId) && recording;
    }

    public synchronized boolean owns(int runId, int handlerId) {
        return same(runId, handlerId);
    }

    public synchronized boolean focusHeld() {
        return focusHeld;
    }

    private boolean same(int runId, int handlerId) {
        return run == runId && handler == handlerId && runId >= 0 && handlerId >= 0;
    }
}
