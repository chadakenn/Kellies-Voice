package com.example.tvvoiceprobe;

import static org.junit.Assert.*;
import org.junit.Test;

public class AnnouncementSpeechSessionTest {
    @Test public void staleCompletionCannotFinishNewSpeech() {
        AnnouncementSpeechSession state = new AnnouncementSpeechSession();
        int first = state.begin();
        int second = state.begin();
        assertFalse(state.finish(first));
        assertTrue(state.isCurrent(second));
        assertTrue(state.finish(second));
        assertFalse(state.finish(second));
    }

    @Test public void cancelInvalidatesLateCallbacks() {
        AnnouncementSpeechSession state = new AnnouncementSpeechSession();
        int first = state.begin();
        state.cancel();
        assertFalse(state.isCurrent(first));
        assertFalse(state.finish(first));
        int second = state.begin();
        assertFalse(state.finish(first));
        assertTrue(state.finish(second));
    }

    @Test public void freshGenerationAfterFailure() {
        AnnouncementSpeechSession state = new AnnouncementSpeechSession();
        int failed = state.begin();
        assertTrue(state.finish(failed));
        int next = state.begin();
        assertTrue(next > failed);
        assertFalse(state.finish(failed));
        assertTrue(state.isCurrent(next));
    }
}
