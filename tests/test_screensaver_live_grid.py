"""Safe opt-in HA camera-grid HLS playback regression checks."""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/example/tvvoiceprobe"


class ScreensaverLiveGridSourceTests(unittest.TestCase):
    def test_camera_grid_uses_existing_snapshot_fallback_and_explicit_hls_players(self):
        board = (JAVA / "ScreensaverBoard.java").read_text()
        live = (JAVA / "ScreensaverLiveGrid.java").read_text()
        self.assertIn("ScreensaverLiveMode.streamLimit", board)
        self.assertIn("new ScreensaverLiveGrid", board)
        self.assertIn("liveGrid.stop()", board)
        self.assertIn("if (liveGrid == null || !liveGrid.isLive(i))", board)
        self.assertIn("request", live.lower())
        self.assertIn('put("type", "camera/stream")', live)
        self.assertIn('put("format", "hls")', live)
        self.assertIn("TvEventPayload.hlsPathAllowed(path)", live)
        self.assertIn('resolved.encodedPath().startsWith("/api/hls/")', live)
        self.assertIn("codec.softwareOnly", live)
        self.assertIn("setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)", live)
        self.assertIn("FIRST_FRAME_TIMEOUT_MS", live)
        self.assertIn("snapshots[slot].setVisibility(View.VISIBLE)", live)
        self.assertIn("socket.close", live)
        self.assertIn("old.release()", live)
        self.assertNotIn("android.media.AudioRecord", live)

    def test_camera_grid_reserves_close_strip_only_once(self):
        board = (JAVA / "ScreensaverBoard.java").read_text()
        camera_grid = board.split("private void showCameras(String[] cameras) {", 1)[1].split(
            "private void refresh(", 1
        )[0]
        # Full-screen Large already reserves a footer below the grid in cardParams.
        self.assertIn("root.addView(grid, cardParams());", camera_grid)
        self.assertIn("if (fraction >= 1f) params.bottomMargin = dp(72);", board)
        # No second footer inside the camera rows, whether HLS or snapshots.
        self.assertIn("grid.setPadding(dp(8), dp(8), dp(8), 0);", camera_grid)
        self.assertNotIn("grid.setPadding(dp(16), dp(16), dp(16), dp(72));", camera_grid)

    def test_home_assistant_setting_is_opt_in_and_shared_with_tv(self):
        consts = (ROOT / "custom_components/kellies_voice/const.py").read_text()
        mapping = (JAVA / "HaLayout.java").read_text()
        local = (JAVA / "OverlayPreferences.java").read_text()
        self.assertIn('"screensaver_live_mode"', consts)
        self.assertIn('"Live (one camera)"', consts)
        self.assertIn('"Live (four cameras, advanced)"', consts)
        self.assertIn('SCREENSAVER_LIVE = "screensaver_live_mode"', mapping)
        self.assertIn("case SCREENSAVER_LIVE:", mapping)
        self.assertIn("ScreensaverLiveMode.SNAPSHOTS", local)
        self.assertIn("pushLayout(HaLayout.SCREENSAVER_LIVE)", (JAVA / "VoiceActivity.java").read_text())


if __name__ == "__main__":
    unittest.main()
