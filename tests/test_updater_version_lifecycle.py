"""Regression gates for release APK identity and updater Activity lifecycle."""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/example/tvvoiceprobe"


class UpdateSecurityTests(unittest.TestCase):
    def test_downloaded_archive_manifest_must_match_release_metadata(self):
        source = (SRC / "UpdateManager.java").read_text()
        policy = (SRC / "UpdateVersionPolicy.java").read_text()
        self.assertIn("verifyInstalledIdentity(temp, release)", source)
        self.assertIn("UpdateVersionPolicy.requireExpected(versionCode(incoming), incoming.versionName", source)
        self.assertIn("archiveCode != offeredCode", policy)
        self.assertIn("archiveName.equals(offeredName)", policy)
        self.assertNotIn("return (int) info.getLongVersionCode()", source)
        self.assertIn("private long installedVersionCode()", source)

    def test_activity_closes_updater_and_callbacks(self):
        activity = (SRC / "VoiceActivity.java").read_text()
        updater = (SRC / "UpdateManager.java").read_text()
        self.assertIn("if (updater != null) updater.close();", activity)
        self.assertIn("if (closed || busy) return;", updater)
        self.assertIn("background.shutdownNow();", updater)
        self.assertIn("if (!closed) runnable.run();", updater)


if __name__ == "__main__":
    unittest.main()
