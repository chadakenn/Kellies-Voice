package com.example.tvvoiceprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public final class LayoutWriteFeedbackTest {
    @Test public void successfulBackgroundWritesDoNotReplaceReadyStatus() {
        LayoutWriteFeedback feedback = new LayoutWriteFeedback();
        assertNull(feedback.onResult(11, "style", true));
        assertNull(feedback.onResult(12, "alert_size", true));
    }

    @Test public void laterSuccessClearsStaleStyleRejection() {
        LayoutWriteFeedback feedback = new LayoutWriteFeedback();
        assertEquals("Home Assistant didn't accept the style change.",
                feedback.onResult(11, "style", false));
        assertEquals("Home Assistant accepted the latest layout changes.",
                feedback.onResult(12, "style", true));
        assertNull(feedback.onResult(13, "alert_position", true));
    }

    @Test public void olderDelayedErrorCannotReplaceSuccessfulLatestResponse() {
        LayoutWriteFeedback feedback = new LayoutWriteFeedback();
        assertNull(feedback.onResult(22, "style", true));
        assertNull(feedback.onResult(21, "style", false));
    }

    @Test public void newerFailureIsStillReported() {
        LayoutWriteFeedback feedback = new LayoutWriteFeedback();
        assertNull(feedback.onResult(20, "style", true));
        assertEquals("Home Assistant didn't accept the alert position change.",
                feedback.onResult(21, "alert_position", false));
    }

    @Test public void reconnectResetsPriorResultOrderingAndError() {
        LayoutWriteFeedback feedback = new LayoutWriteFeedback();
        assertEquals("Home Assistant didn't accept the style change.",
                feedback.onResult(40, "style", false));
        feedback.reset();
        assertNull(feedback.onResult(1, "style", true));
        assertEquals("Home Assistant didn't accept the style change.",
                feedback.onResult(2, "style", false));
    }
}
