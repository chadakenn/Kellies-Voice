package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiveCameraModeTest {
    @Test public void missingAndUnknownSettingsRemainSafe() {
        assertEquals(LiveCameraMode.SOFTWARE, LiveCameraMode.normalize(null));
        assertEquals(LiveCameraMode.SOFTWARE, LiveCameraMode.normalize(""));
        assertEquals(LiveCameraMode.SOFTWARE, LiveCameraMode.normalize("auto"));
        assertTrue(LiveCameraMode.softwareOnly("invalid"));
    }

    @Test public void snapshotsNeverStartPlayer() {
        assertFalse(LiveCameraMode.requestsStream(LiveCameraMode.SNAPSHOT));
        assertTrue(LiveCameraMode.requestsStream(LiveCameraMode.SOFTWARE));
    }

    @Test public void hardwareRequiresExplicitMode() {
        assertTrue(LiveCameraMode.requestsStream(LiveCameraMode.HARDWARE));
        assertFalse(LiveCameraMode.softwareOnly(LiveCameraMode.HARDWARE));
        assertTrue(LiveCameraMode.softwareOnly(LiveCameraMode.SOFTWARE));
        assertEquals("Hardware (advanced)", LiveCameraMode.label(LiveCameraMode.HARDWARE));
    }
}
