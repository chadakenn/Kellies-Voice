package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicrophonePrivacyTest {
    @Test public void android14WaitsWithoutTheMicrophoneType() {
        assertEquals(MicrophonePrivacy.TYPE_SPECIAL_USE, MicrophonePrivacy.serviceType(34, false));
        assertEquals(MicrophonePrivacy.TYPE_SPECIAL_USE, MicrophonePrivacy.serviceType(35, false));
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE, MicrophonePrivacy.serviceType(34, true));
    }

    @Test public void olderAndroidKeepsTheMicrophoneTypeWhileTheOverlayIsOpen() {
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE, MicrophonePrivacy.serviceType(33, false));
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE, MicrophonePrivacy.serviceType(29, true));
        assertEquals(MicrophonePrivacy.TYPE_NONE, MicrophonePrivacy.serviceType(28, true));
    }

    @Test public void rejectedSpecialUseDoesNotClaimTheMicrophoneIsOff() {
        int actual = MicrophonePrivacy.afterRejection(MicrophonePrivacy.TYPE_SPECIAL_USE, true);
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE, actual);
        String text = MicrophonePrivacy.notificationText(false, actual);
        assertEquals(MicrophonePrivacy.MIC_STUCK, text);
        assertFalse(text.contains("off"));
        assertEquals(MicrophonePrivacy.MIC_OFF,
                MicrophonePrivacy.notificationText(false, MicrophonePrivacy.TYPE_SPECIAL_USE));
        assertEquals(MicrophonePrivacy.MIC_ON,
                MicrophonePrivacy.notificationText(true, MicrophonePrivacy.TYPE_MICROPHONE));
    }

    @Test public void followUpPreviewIsAMicrophoneEvenWhenAssistIsNotListening() {
        MicOwnership owner = new MicOwnership();
        assertFalse(owner.microphoneActive());
        assertEquals(MicOwnership.PREVIEW, owner.armPreview());
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE,
                MicrophonePrivacy.serviceType(35, owner.microphoneActive()));
        String text = MicrophonePrivacy.notificationText(true,
                MicrophonePrivacy.TYPE_MICROPHONE, owner.followUpCapture());
        assertEquals(MicrophonePrivacy.MIC_FOLLOW, text);
        assertFalse(text.toLowerCase().contains("off"));
    }

    @Test public void handoffDoesNotPassThroughTheIdleForegroundType() {
        MicOwnership owner = new MicOwnership();
        owner.armPreview();
        int during = owner.beginHandoff();
        assertEquals(MicOwnership.HANDOFF, during);
        assertTrue(owner.microphoneActive());
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE,
                MicrophonePrivacy.serviceType(35, true));
        assertEquals(MicOwnership.ASSIST, owner.assistListening());
        owner.bindRun(7);
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE,
                MicrophonePrivacy.serviceType(35, owner.microphoneActive()));
        assertEquals(MicOwnership.IDLE, owner.captureReleased(7));
        assertEquals(MicrophonePrivacy.TYPE_SPECIAL_USE,
                MicrophonePrivacy.serviceType(35, owner.microphoneActive()));
    }

    @Test public void stopAndSilenceStayOnTheMicrophoneUntilThatRunReleases() {
        MicOwnership owner = new MicOwnership();
        owner.armAssist();
        owner.bindRun(4);
        assertEquals(MicOwnership.ASSIST, owner.stopRequested());
        assertEquals(MicOwnership.ASSIST, owner.releaseAll());
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE,
                MicrophonePrivacy.serviceType(35, owner.microphoneActive()));
        assertEquals(MicOwnership.ASSIST, owner.captureReleased(3));
        assertEquals(MicOwnership.IDLE, owner.captureReleased(4));
        assertEquals(MicrophonePrivacy.TYPE_SPECIAL_USE,
                MicrophonePrivacy.serviceType(35, owner.microphoneActive()));
    }

    @Test public void aLateReleaseCannotClearTheNextRecording() {
        MicOwnership owner = new MicOwnership();
        owner.armAssist();
        owner.bindRun(4);
        owner.armAssist();
        assertEquals(MicOwnership.ASSIST, owner.captureReleased(4));
        assertTrue(owner.microphoneActive());
        owner.bindRun(9);
        assertEquals(MicOwnership.ASSIST, owner.captureReleased(4));
        assertEquals(MicrophonePrivacy.TYPE_MICROPHONE,
                MicrophonePrivacy.serviceType(35, owner.microphoneActive()));
        assertEquals(MicOwnership.IDLE, owner.captureReleased(9));
    }

    @Test public void aCaptureThatNeverOpenedDoesNotWaitForAWorker() {
        MicOwnership owner = new MicOwnership();
        owner.beginHandoff();
        assertEquals(MicOwnership.IDLE, owner.captureNeverStarted());
        assertFalse(owner.microphoneActive());
        owner.armAssist();
        owner.bindRun(2);
        owner.armAssist();
        assertEquals(MicOwnership.ASSIST, owner.captureNeverStarted());
        assertTrue(owner.microphoneActive());
    }

    @Test public void everyStopReleasesOnlyAfterTheRecorderIsGone() {
        assertEquals(MicOwnership.IDLE, released(MicOwnership.PREVIEW).previewReleased());
        MicOwnership handoff = new MicOwnership();
        handoff.armPreview();
        handoff.beginHandoff();
        assertEquals(MicOwnership.HANDOFF, handoff.previewReleased());
        for (int start : new int[] {MicOwnership.PREVIEW, MicOwnership.HANDOFF, MicOwnership.ASSIST}) {
            MicOwnership stop = phase(start);
            if (start == MicOwnership.PREVIEW) {
                assertEquals(MicOwnership.IDLE, stop.releaseAll());
            } else {
                assertEquals(start == MicOwnership.HANDOFF
                        ? MicOwnership.HANDOFF : MicOwnership.ASSIST, stop.releaseAll());
                assertTrue(stop.microphoneActive());
            }
        }
        MicOwnership failed = new MicOwnership();
        failed.armPreview();
        assertEquals(MicOwnership.IDLE, failed.previewDidNotOpen());
        MicOwnership assist = new MicOwnership();
        assist.armAssist();
        assist.armPreview();
        assertEquals(MicOwnership.ASSIST, assist.phase());
        assertEquals(MicOwnership.IDLE, assist.captureNeverStarted());
    }

    private static MicOwnership released(int phase) {
        return phase(phase);
    }

    private static MicOwnership phase(int phase) {
        MicOwnership owner = new MicOwnership();
        if (phase == MicOwnership.PREVIEW) owner.armPreview();
        else if (phase == MicOwnership.HANDOFF) {
            owner.armPreview();
            owner.beginHandoff();
        } else if (phase == MicOwnership.ASSIST) owner.armAssist();
        return owner;
    }
}
