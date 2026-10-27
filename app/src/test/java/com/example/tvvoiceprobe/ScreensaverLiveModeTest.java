package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScreensaverLiveModeTest {
    @Test public void snapshotGridRemainsDefault() {
        assertEquals(ScreensaverLiveMode.SNAPSHOTS, ScreensaverLiveMode.normalize(null));
        assertEquals(ScreensaverLiveMode.SNAPSHOTS, ScreensaverLiveMode.normalize("wrong"));
        assertEquals(0, ScreensaverLiveMode.streamLimit(
                ScreensaverLiveMode.SNAPSHOTS, LiveCameraMode.SOFTWARE, 4));
    }

    @Test public void liveDecoderLimitsRequireExplicitOptIn() {
        assertEquals(1, ScreensaverLiveMode.streamLimit(
                ScreensaverLiveMode.ONE, LiveCameraMode.SOFTWARE, 4));
        assertEquals(2, ScreensaverLiveMode.streamLimit(
                ScreensaverLiveMode.TWO, LiveCameraMode.SOFTWARE, 4));
        assertEquals(4, ScreensaverLiveMode.streamLimit(
                ScreensaverLiveMode.FOUR, LiveCameraMode.SOFTWARE, 4));
        assertEquals(1, ScreensaverLiveMode.streamLimit(
                ScreensaverLiveMode.FOUR, LiveCameraMode.SOFTWARE, 1));
        assertEquals(0, ScreensaverLiveMode.streamLimit(
                ScreensaverLiveMode.FOUR, LiveCameraMode.SNAPSHOT, 4));
        assertEquals(2, ScreensaverLiveMode.streamLimit(
                ScreensaverLiveMode.TWO, LiveCameraMode.HARDWARE, 4));
        assertEquals(0, ScreensaverLiveMode.streamLimit(
                ScreensaverLiveMode.FOUR, LiveCameraMode.SOFTWARE, 0));
    }

    @Test public void authenticatedHlsPathValidationIsSharedWithWorkingPopup() {
        assertTrue(TvEventPayload.hlsPathAllowed("/api/hls/abcdef/playlist.m3u8"));
        assertFalse(TvEventPayload.hlsPathAllowed("http://untrusted.example/hls.m3u8"));
        assertFalse(TvEventPayload.hlsPathAllowed("/api/hls/../malicious.m3u8"));
        assertFalse(TvEventPayload.hlsPathAllowed("/api/hls/abc.m3u8?token=secret"));
    }
}
