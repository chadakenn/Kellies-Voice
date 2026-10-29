package com.example.tvvoiceprobe;

/**
 * Pure-Java state gate for a temporary, microphone-free Home Assistant
 * login check. A credential must prove BOTH Assist capability and
 * per-TV scoped-alert permissions before another component may consider
 * activating it. This gate itself never saves or activates credentials.
 *
 * Command IDs are fixed for this one-shot isolated WebSocket only.
 */
public final class PairingAssistProofState {
    public static final int PIPELINES_REQUEST_ID = 77111;
    public static final int SCOPED_ALERTS_REQUEST_ID = 77112;
    public enum Stage {
        WAIT_CHALLENGE, WAIT_AUTH, WAIT_PIPELINES,
        WAIT_SCOPED_ALERTS, VERIFIED, FAILED
    }

    private Stage stage = Stage.WAIT_CHALLENGE;

    public synchronized Stage stage() { return stage; }

    public synchronized boolean terminal() {
        return stage == Stage.VERIFIED || stage == Stage.FAILED;
    }

    public synchronized boolean challenge() {
        if (stage != Stage.WAIT_CHALLENGE) {
            stage = Stage.FAILED;
            return false;
        }
        stage = Stage.WAIT_AUTH;
        return true;
    }

    public synchronized boolean authenticated() {
        if (stage != Stage.WAIT_AUTH) {
            stage = Stage.FAILED;
            return false;
        }
        stage = Stage.WAIT_PIPELINES;
        return true;
    }

    /** Successful read-only Assist response allows the alert check, not activation. */
    public synchronized boolean pipelineResult(
            int id, boolean success, boolean hasPipeline) {
        if (stage != Stage.WAIT_PIPELINES || id != PIPELINES_REQUEST_ID
                || !success || !hasPipeline) {
            stage = Stage.FAILED;
            return false;
        }
        stage = Stage.WAIT_SCOPED_ALERTS;
        return true;
    }

    /** Only authorized per-TV alert subscription completes both proofs. */
    public synchronized boolean scopedAlertResult(int id, boolean success) {
        if (stage != Stage.WAIT_SCOPED_ALERTS
                || id != SCOPED_ALERTS_REQUEST_ID || !success) {
            stage = Stage.FAILED;
            return false;
        }
        stage = Stage.VERIFIED;
        return true;
    }

    /** Failure is sticky, including if called after a previous success. */
    public synchronized void fail() {
        stage = Stage.FAILED;
    }
}
