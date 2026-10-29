package com.example.tvvoiceprobe;

/**
 * Pure-Java state gate for a temporary, microphone-free Home Assistant
 * authenticated Assist capability check. Not a pairing/credential commit.
 *
 * Never declare success after auth_ok alone. A matching successful read-only
 * assist_pipeline/pipeline/list result is required.
 */
public final class PairingAssistProofState {
    public static final int PIPELINES_REQUEST_ID = 77111;
    public enum Stage { WAIT_CHALLENGE, WAIT_AUTH, WAIT_PIPELINES, VERIFIED, FAILED }
    private Stage stage = Stage.WAIT_CHALLENGE;

    public synchronized Stage stage() { return stage; }
    public synchronized boolean terminal() {
        return stage == Stage.VERIFIED || stage == Stage.FAILED;
    }

    /** Valid HA auth challenge seen. Caller should now send auth token. */
    public synchronized boolean challenge() {
        if (stage != Stage.WAIT_CHALLENGE) {
            stage = Stage.FAILED;
            return false;
        }
        stage = Stage.WAIT_AUTH;
        return true;
    }

    /** Valid HA auth_ok seen. Caller should request a read-only Assist list. */
    public synchronized boolean authenticated() {
        if (stage != Stage.WAIT_AUTH) {
            stage = Stage.FAILED;
            return false;
        }
        stage = Stage.WAIT_PIPELINES;
        return true;
    }

    /** Only an exact result ID with success=true can pass the proof gate. */
    public synchronized boolean pipelineResult(int id, boolean success) {
        if (stage != Stage.WAIT_PIPELINES || id != PIPELINES_REQUEST_ID || !success) {
            stage = Stage.FAILED;
            return false;
        }
        stage = Stage.VERIFIED;
        return true;
    }

    public synchronized void fail() {
        if (stage != Stage.VERIFIED) stage = Stage.FAILED;
    }
}
