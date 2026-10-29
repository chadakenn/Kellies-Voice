package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.*;

/** No Android or network dependencies: proof must not pass on auth_ok alone. */
public class PairingAssistProofStateTest {
    @Test public void challengeAuthAndExactAssistResultRequired() {
        PairingAssistProofState state = new PairingAssistProofState();
        assertEquals(PairingAssistProofState.Stage.WAIT_CHALLENGE, state.stage());
        assertFalse(state.terminal());
        assertTrue(state.challenge());
        assertEquals(PairingAssistProofState.Stage.WAIT_AUTH, state.stage());
        assertTrue(state.authenticated());
        assertEquals(PairingAssistProofState.Stage.WAIT_PIPELINES, state.stage());
        assertFalse(state.terminal());
        assertTrue(state.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true));
        assertTrue(state.terminal());
        assertEquals(PairingAssistProofState.Stage.VERIFIED, state.stage());
    }

    @Test public void authOkWithoutChallengeMustFail() {
        PairingAssistProofState state = new PairingAssistProofState();
        assertFalse(state.authenticated());
        assertEquals(PairingAssistProofState.Stage.FAILED, state.stage());
        assertFalse(state.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true));
    }

    @Test public void pipelineSuccessWithoutAuthMustFail() {
        PairingAssistProofState state = new PairingAssistProofState();
        assertFalse(state.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true));
        assertEquals(PairingAssistProofState.Stage.FAILED, state.stage());
    }

    @Test public void wrongIdFailedResultOrReplayCannotPass() {
        PairingAssistProofState wrong = ready();
        assertFalse(wrong.pipelineResult(22, true));
        assertEquals(PairingAssistProofState.Stage.FAILED, wrong.stage());
        assertFalse(wrong.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true));

        PairingAssistProofState failed = ready();
        assertFalse(failed.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, false));
        assertEquals(PairingAssistProofState.Stage.FAILED, failed.stage());

        PairingAssistProofState repeated = ready();
        assertFalse(repeated.challenge());
        assertEquals(PairingAssistProofState.Stage.FAILED, repeated.stage());
    }

    @Test public void cancellationNeverTurnsIntoSuccess() {
        PairingAssistProofState state = ready();
        state.fail();
        assertTrue(state.terminal());
        assertEquals(PairingAssistProofState.Stage.FAILED, state.stage());
        assertFalse(state.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true));
    }

    private static PairingAssistProofState ready() {
        PairingAssistProofState state = new PairingAssistProofState();
        assertTrue(state.challenge());
        assertTrue(state.authenticated());
        return state;
    }
}
