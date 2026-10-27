"""Source-level regression for Android updater and GitHub provenance workflow."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ReleaseSecurityHardeningTest(unittest.TestCase):
    def test_in_app_updater_checks_actual_archive_version(self):
        root = ROOT / "app/src/main/java/com/example/tvvoiceprobe"
        updater = (root / "UpdateManager.java").read_text()
        policy = (root / "UpdateVersionPolicy.java").read_text()
        assert "verifyInstalledIdentity(temp, release)" in updater
        assert "UpdateVersionPolicy.requireExpected(versionCode(incoming), incoming.versionName" in updater
        assert "return (int) info.getLongVersionCode()" not in updater
        assert "private long installedVersionCode()" in updater
        assert "archiveCode != offeredCode" in policy
        assert "archiveName.equals(offeredName)" in policy

    def test_activity_closes_updater_and_discards_callbacks(self):
        activity = (ROOT / "app/src/main/java/com/example/tvvoiceprobe/VoiceActivity.java").read_text()
        updater = (ROOT / "app/src/main/java/com/example/tvvoiceprobe/UpdateManager.java").read_text()
        assert "if (updater != null) updater.close();" in activity
        assert "background.shutdownNow();" in updater
        assert "ui.removeCallbacksAndMessages(null);" in updater
        assert "if (!closed) runnable.run();" in updater

    def test_signed_apk_workflows_attest_exact_built_binary(self):
        for name in ("build-apk.yml", "dev-apk.yml", "publish-hacs-beta.yml"):
            workflow = (ROOT / ".github/workflows" / name).read_text()
            with self.subTest(name=name):
                self.assertIn("uses: actions/attest@v4", workflow)
                self.assertIn("attestations: write", workflow)
                self.assertIn("id-token: write", workflow)
                self.assertIn(
                    "subject-path: app/build/outputs/apk/release/app-release.apk", workflow
                )


if __name__ == "__main__":
    unittest.main()
