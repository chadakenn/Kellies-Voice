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

    def test_remote_focus_and_back_are_consistent_across_tv_hosts(self):
        board = (JAVA / "ScreensaverBoard.java").read_text()
        dream = (JAVA / "FrigateScreensaverDream.java").read_text()
        preview = (JAVA / "ScreensaverPreviewActivity.java").read_text()
        self.assertIn("cell.setFocusable(true)", board)
        self.assertIn("cell.setOnClickListener(v -> showFullscreen(slot, cameras[slot]))", board)
        self.assertIn("android.R.attr.state_focused", board)
        self.assertIn("Back to cameras", board)
        self.assertIn("private void exitFullscreen()", board)
        self.assertIn("if (handleBack()) return;", board)
        self.assertIn("board.handleBack()", dream)
        self.assertIn("board.handleBack()", preview)
        self.assertIn("gridCells[slot].requestFocus()", board)
        self.assertIn("AspectRatioFrameLayout.RESIZE_MODE_FIT", board)

    def test_remote_back_reaches_board_even_when_child_button_is_focused(self):
        board = (JAVA / "ScreensaverBoard.java").read_text()
        # The old root OnKeyListener lost Back events while a footer button had
        # focus. ViewGroup dispatchKeyEvent sees the event before its children.
        self.assertIn("@Override public boolean dispatchKeyEvent(KeyEvent event)", board)
        self.assertIn("if (event.getKeyCode() == KeyEvent.KEYCODE_BACK)", board)
        self.assertIn("event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()", board)
        self.assertIn("return true; // Consume DOWN and UP", board)
        self.assertNotIn("root.setOnKeyListener((view, key, event)", board)
        # Android 13+ system Back does not always send a legacy key event.
        self.assertIn("view.findOnBackInvokedDispatcher()", board)
        self.assertIn("dispatcher.registerOnBackInvokedCallback(", board)
        self.assertIn("overlayBackDispatcher.unregisterOnBackInvokedCallback(", board)
        self.assertIn("context instanceof ScreensaverWatchService", board)
        # Shared handler must not break Frigate webpage remote browsing.
        self.assertIn("if (handleBack()) return;", board)
        self.assertIn("if (browsing && web != null)", board)
        self.assertIn("if (web.canGoBack())", board)
        self.assertIn("host.close();", board)

    def test_fullscreen_and_close_buttons_have_visible_tv_remote_focus(self):
        board = (JAVA / "ScreensaverBoard.java").read_text()
        self.assertIn("back.setBackground(controlBackground(", board)
        self.assertIn("close.setBackground(controlBackground(", board)
        self.assertIn("0xFFFFD166", board)  # Amber focused border.
        self.assertIn("android.R.attr.state_focused", board)
        self.assertIn("back.setNextFocusRightId(closeControl.getId());", board)
        self.assertIn("closeControl.setNextFocusLeftId(back.getId());", board)
        self.assertIn("back.post(() -> { if (!stopped && fullscreenSlot == slot) back.requestFocus(); });", board)
        self.assertIn("closeControl.setNextFocusLeftId(View.NO_ID);", board)
        self.assertIn("if (liveGrid != null) liveGrid.clearFullscreen();", board)

    def test_fullscreen_audio_is_explicit_and_always_muted_on_exit(self):
        board = (JAVA / "ScreensaverBoard.java").read_text()
        live = (JAVA / "ScreensaverLiveGrid.java").read_text()
        self.assertIn("liveGrid.selectFullscreen(slot, fullVideo, audioLabel)", board)
        self.assertIn("liveGrid.clearFullscreen()", board)
        self.assertIn("setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)", live)
        self.assertIn("setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)", live)
        self.assertIn("player.setVolume(0f)", live)
        self.assertIn("player.setVolume(1f)", live)
        self.assertIn("selected.setVolume(0f)", live)
        self.assertIn("tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)", live)
        self.assertIn("No audio track in camera stream", live)
        self.assertIn("void clearFullscreen()", live)
        self.assertIn("clearFullscreen();\n        stopped = true;", live)
        self.assertIn("onVideoSizeChanged(VideoSize size)", live)
        self.assertIn("old.release()", live)
        self.assertNotIn("AudioRecord", live)
        self.assertNotIn("MediaRecorder", board)

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
