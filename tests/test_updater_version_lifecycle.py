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

    def test_install_revalidates_persisted_receipt_before_intent(self):
        updater = (SRC / "UpdateManager.java").read_text()
        receipt = (SRC / "VerifiedApkReceipt.java").read_text()
        self.assertIn("new VerifiedApkReceipt(release.code, release.name, release.sha256", updater)
        self.assertIn(".save(receiptFile());", updater)
        self.assertIn("VerifiedApkReceipt.read(receiptFile())", updater)
        self.assertIn("receipt.verifyFile(apk, channel)", updater)
        self.assertIn("verifyInstalledIdentity(apk, new Release(receipt.code", updater)
        self.assertIn("if (epoch == offerEpoch) openVerifiedInstaller();", updater)
        self.assertIn("sha256.equals(hex.toString())", receipt)
        self.assertIn("app-release.verified", updater)

    def test_activity_closes_updater_and_callbacks(self):
        activity = (SRC / "VoiceActivity.java").read_text()
        updater = (SRC / "UpdateManager.java").read_text()
        self.assertIn("if (updater != null) updater.close();", activity)
        self.assertIn("if (closed || busy) return;", updater)
        self.assertIn("background.shutdownNow();", updater)
        self.assertIn("if (!closed) runnable.run();", updater)


if __name__ == "__main__":
    unittest.main()
