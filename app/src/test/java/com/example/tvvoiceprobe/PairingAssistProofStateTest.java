package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** A valid token is not enough; Assist and per-TV scoped alerts must both pass. */
public class PairingAssistProofStateTest {
    @Test public void onlyAssistAndScopedAlertChecksTogetherPass() {
        PairingAssistProofState state = new PairingAssistProofState();
        assertEquals(PairingAssistProofState.Stage.WAIT_CHALLENGE, state.stage());
        assertFalse(state.terminal());
        assertTrue(state.challenge());
        assertTrue(state.authenticated());
        assertEquals(PairingAssistProofState.Stage.WAIT_PIPELINES, state.stage());
        assertTrue(state.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true, true));
        // Auth and Assist alone must never claim a usable paired TV.
        assertEquals(PairingAssistProofState.Stage.WAIT_SCOPED_ALERTS, state.stage());
        assertFalse(state.terminal());
        assertTrue(state.scopedAlertResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));
        assertTrue(state.terminal());
        assertEquals(PairingAssistProofState.Stage.VERIFIED, state.stage());
    }

    @Test public void authOkWithoutChallengeMustFail() {
        PairingAssistProofState state = new PairingAssistProofState();
        assertFalse(state.authenticated());
        assertEquals(PairingAssistProofState.Stage.FAILED, state.stage());
        assertFalse(state.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true, true));
        assertFalse(state.scopedAlertResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));
    }

    @Test public void noPipelineOrFailedPipelineCannotAdvance() {
        PairingAssistProofState noPipeline = readyToReadPipelines();
        assertFalse(noPipeline.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true, false));
        assertEquals(PairingAssistProofState.Stage.FAILED, noPipeline.stage());

        PairingAssistProofState failed = readyToReadPipelines();
        assertFalse(failed.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, false, true));
        assertEquals(PairingAssistProofState.Stage.FAILED, failed.stage());

        PairingAssistProofState wrongId = readyToReadPipelines();
        assertFalse(wrongId.pipelineResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true, true));
        assertEquals(PairingAssistProofState.Stage.FAILED, wrongId.stage());
    }

    @Test public void scopedAlertsDeniedOrWrongRequestIdMustFail() {
        PairingAssistProofState noPermission = readyForAlerts();
        assertFalse(noPermission.scopedAlertResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, false));
        assertEquals(PairingAssistProofState.Stage.FAILED, noPermission.stage());

        PairingAssistProofState wrong = readyForAlerts();
        assertFalse(wrong.scopedAlertResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true));
        assertEquals(PairingAssistProofState.Stage.FAILED, wrong.stage());
    }

    @Test public void outOfOrderResponseAndReplayMustFail() {
        PairingAssistProofState outOfOrder = readyToReadPipelines();
        assertFalse(outOfOrder.scopedAlertResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));
        assertTrue(outOfOrder.terminal());
        assertEquals(PairingAssistProofState.Stage.FAILED, outOfOrder.stage());

        PairingAssistProofState repeat = readyForAlerts();
        assertFalse(repeat.authenticated());
        assertFalse(repeat.scopedAlertResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));
        assertEquals(PairingAssistProofState.Stage.FAILED, repeat.stage());
    }

    @Test public void cancellationOrFailureIsSticky() {
        PairingAssistProofState state = readyForAlerts();
        state.fail();
        assertTrue(state.terminal());
        assertFalse(state.scopedAlertResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));

        PairingAssistProofState verified = readyForAlerts();
        assertTrue(verified.scopedAlertResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));
        verified.fail();
        assertEquals(PairingAssistProofState.Stage.FAILED, verified.stage());
    }

    private static PairingAssistProofState readyToReadPipelines() {
        PairingAssistProofState state = new PairingAssistProofState();
        assertTrue(state.challenge());
        assertTrue(state.authenticated());
        return state;
    }

    private static PairingAssistProofState readyForAlerts() {
        PairingAssistProofState state = readyToReadPipelines();
        assertTrue(state.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true, true));
        return state;
    }
}
