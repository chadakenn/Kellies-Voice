package com.example.tvvoiceprobe;

/**
 * Which recorder the voice overlay is responsible for.
 * Idle is the only phase that may use the non-microphone foreground type.
 * Assist and handoff stay on the microphone type until the matching run
 * reports that AudioRecord.release has returned. A late release from an
 * older run cannot clear a newer one.
 * The wake-word service is separate and releases its recorder before this overlay opens.
 */
public final class MicOwnership {
    public static final int IDLE = 0;
    public static final int PREVIEW = 1;
    public static final int HANDOFF = 2;
    public static final int ASSIST = 3;

    private int phase = IDLE;
    /** Run whose recorder may still be open. Zero means none. */
    private int heldRun;
    /** A capture was requested and has not been bound to a run yet. */
    private boolean arming;

    public int phase() { return phase; }

    public boolean microphoneActive() { return phase != IDLE; }

    /** Follow-up preview, including the moment the same recorder is handed to Assist. */
    public boolean followUpCapture() { return phase == PREVIEW || phase == HANDOFF; }

    /** Call before the follow-up preview opens a recorder. Does not interrupt Assist. */
    public int armPreview() {
        if (phase == ASSIST || phase == HANDOFF) return phase;
        phase = PREVIEW;
        return phase;
    }

    /** Preview failed before a recorder existed. */
    public int previewDidNotOpen() {
        if (phase == PREVIEW) phase = IDLE;
        return phase;
    }

    /**
     * The preview recorder has been released (timeout or miss).
     * A handoff keeps the same recorder, so this does not clear that phase.
     */
    public int previewReleased() {
        if (phase == PREVIEW) phase = IDLE;
        return phase;
    }

    /** The preview recorder is moving to Assist. This must not pass through idle. */
    public int beginHandoff() {
        phase = HANDOFF;
        arming = true;
        return phase;
    }

    /** Assist is about to open a recorder. The run id is bound after talk() succeeds. */
    public int armAssist() {
        phase = ASSIST;
        arming = true;
        return phase;
    }

    /** The pipeline accepted this run. Later releases of other runs are ignored. */
    public int bindRun(int runId) {
        if (runId <= 0) return phase;
        if (phase == ASSIST || phase == HANDOFF) {
            heldRun = runId;
            arming = false;
        }
        return phase;
    }

    /** The Assist worker reported that this run is recording. Does not change who owns the release. */
    public int assistListening() {
        if (phase == HANDOFF) phase = ASSIST;
        return phase;
    }

    /** STOP, silence, or the listening cap asked the worker to stop. The type stays on. */
    public int stopRequested() {
        return phase;
    }

    /**
     * AudioRecord.release has returned for this run.
     * A different run, or a release that arrives while a new capture is arming, does not go idle.
     */
    public int captureReleased(int runId) {
        if (heldRun != 0 && runId == heldRun) heldRun = 0;
        else return phase;
        if (arming) return phase;
        if (phase == ASSIST || phase == HANDOFF) phase = IDLE;
        return phase;
    }

    /** talk() failed or the pipeline never started a recorder for the attempt now in progress. */
    public int captureNeverStarted() {
        arming = false;
        if (heldRun != 0) return phase;
        if (phase == ASSIST || phase == HANDOFF) phase = IDLE;
        return phase;
    }

    /**
     * Preview stops are synchronous, so preview may go idle here.
     * Assist and handoff wait for {@link #captureReleased(int)} when a run is still held.
     */
    public int releaseAll() {
        if (phase == PREVIEW) {
            phase = IDLE;
            return phase;
        }
        if (arming || heldRun != 0) return phase;
        if (phase == ASSIST || phase == HANDOFF) phase = IDLE;
        return phase;
    }
}
