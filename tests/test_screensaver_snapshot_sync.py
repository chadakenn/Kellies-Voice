"""Regression: HA-selected camera grid cannot fail silently on Android TV."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/example/tvvoiceprobe"


def source(name):
    return (JAVA / name).read_text(encoding="utf-8")


class SnapshotSyncDiagnosticsTest(unittest.TestCase):
    def test_idle_overlay_has_explanation_when_ha_source_not_yet_synced(self):
        watcher = source("ScreensaverWatchService.java")
        board = source("ScreensaverBoard.java")
        self.assertIn("boolean awaitingHaCameras = !configured && prefs.haManage();", watcher)
        self.assertIn("configured || awaitingHaCameras", watcher)
        self.assertIn("No Frigate URL or HA cameras are saved on this TV", board)
        self.assertIn("void showCameras(String[] cameras)", board)

    def test_device_diagnostics_separate_ha_and_tv_states(self):
        activity = source("VoiceActivity.java")
        watcher = source("ScreensaverWatchStatus.java")
        self.assertIn("Cameras saved on TV:", activity)
        self.assertIn("Home Assistant layout:", activity)
        self.assertIn("Source on TV:", activity)
        self.assertIn("HA settings link:", activity)
        self.assertIn("Last HA source:", activity)
        self.assertIn("ScreensaverWatchStatus.settingsSync()", activity)
        self.assertIn("ScreensaverWatchStatus.sourceSync()", activity)
        self.assertIn("static void settingsSync", watcher)
        self.assertIn("static void sourceSync", watcher)

    def test_manual_resync_is_opt_in_and_retains_dream_receiver(self):
        activity = source("VoiceActivity.java")
        watcher = source("ScreensaverWatchService.java")
        sync = source("ScreensaverSettingsSync.java")
        self.assertIn("Resync HA cameras", activity)
        self.assertIn("ScreensaverWatchService.refreshFromHa(this)", activity)
        self.assertIn("ACTION_REFRESH_HA", watcher)
        self.assertIn("settingsSync.stop()", watcher)
        self.assertIn("registerReceiver(dreams, filter", watcher)
        self.assertIn('HA camera list received: ', sync)
        self.assertIn("ScreensaverSettingsEffects.apply(context, setting)", sync)
        self.assertIn("onMessage(WebSocket ws, String raw)", sync)
        self.assertNotIn('put("access_token", token);', watcher)
        self.assertNotIn("AudioRecord", sync)


if __name__ == "__main__":
    unittest.main()
