package com.example.tvvoiceprobe;

import static org.junit.Assert.*;
import org.junit.Test;

public final class AssistReadinessPolicyTest {
    @Test public void explicitPendingMicRequestExpiresAtThirtySeconds() {
        long requestedAt = 10_000L;
        long until = requestedAt + AssistReadinessPolicy.PENDING_USER_REQUEST_MS;
        assertTrue(AssistReadinessPolicy.mayStartPendingRequest(true, false, requestedAt, until));
        assertTrue(AssistReadinessPolicy.mayStartPendingRequest(true, false, until, until));
        assertFalse(AssistReadinessPolicy.mayStartPendingRequest(true, false, until + 1, until));
        assertFalse(AssistReadinessPolicy.mayStartPendingRequest(false, false, requestedAt, until));
        assertFalse(AssistReadinessPolicy.mayStartPendingRequest(true, true, requestedAt, until));
        assertFalse(AssistReadinessPolicy.mayStartPendingRequest(true, false, 100, -1));
    }

    @Test public void waitingDoesNotPretendToHaveStartedSpeech() {
        assertTrue(AssistReadinessPolicy.waitingForConnection().contains("Connecting"));
        assertTrue(AssistReadinessPolicy.waitingForConnection().contains("Press TALK"));
        assertTrue(AssistReadinessPolicy.expiredRequest().contains("expired"));
    }

    @Test public void warmupErrorsAreSpecificButNeverStartAnotherRequest() {
        assertTrue(AssistReadinessPolicy.assistError("pipeline-not-loaded", "")
                .contains("pipeline is starting"));
        assertTrue(AssistReadinessPolicy.assistError("pipeline_unavailable",
                "Assist pipeline is initializing").contains("pipeline is starting"));
        assertTrue(AssistReadinessPolicy.assistError("pipeline-not-found", "")
                .contains("selected Home Assistant Assist pipeline is unavailable"));
    }

    @Test public void speechAgentAndTtsErrorsHaveIndependentMessages() {
        assertTrue(AssistReadinessPolicy.assistError("stt-stream-failed", "")
                .startsWith("Speech recognition failed"));
        assertTrue(AssistReadinessPolicy.assistError("intent-failed", "")
                .startsWith("Home Assistant conversation agent failed"));
        assertTrue(AssistReadinessPolicy.assistError("tts-failed", "")
                .startsWith("Home Assistant text-to-speech failed"));
    }

    @Test public void serverDetailsAreNeverPrintedOnScreen() {
        String privateData = "https://my-ha.internal/api?access_token=privatevalue";
        assertFalse(AssistReadinessPolicy.assistError("intent-failed", privateData).contains("privatevalue"));
        assertFalse(AssistReadinessPolicy.assistError("unknown", privateData).contains("my-ha"));
        assertFalse(AssistReadinessPolicy.assistError(null, null).contains("null"));
    }
}
