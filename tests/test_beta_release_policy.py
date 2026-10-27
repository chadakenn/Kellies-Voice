"""Guard opt-in beta update precedence and reversible release pruning."""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def core(version):
    m = re.fullmatch(r"v?(\d+)\.(\d+)\.(\d+)(?:-dev\.(\d+))?", version)
    if not m:
        raise ValueError(version)
    return tuple(map(int, m.groups()[:3]))


class BetaReleasePolicyTests(unittest.TestCase):
    def test_new_beta_version_is_newer_than_stable_and_matching(self):
        build = (ROOT / "app/build.gradle").read_text()
        manifest = (ROOT / "custom_components/kellies_voice/manifest.json").read_text()
        self.assertIn("versionName '0.16.13-dev.1'", build)
        self.assertIn("versionCode 53", build)
        self.assertIn('"version": "0.16.13-dev.1"', manifest)
        self.assertGreater(core("0.16.13-dev.1"), core("v0.16.12"))
        self.assertEqual(core("v0.16.12-dev.11"), core("v0.16.12"))
        self.assertLess((0, 16, 12, -11), (0, 16, 12, 0))

    def test_release_workflow_rejects_beta_from_stable_core(self):
        source = (ROOT / ".github/workflows/publish-hacs-beta.yml").read_text()
        self.assertIn("base <= current:", source)
        self.assertIn("Beta base must be newer than stable", source)
        self.assertIn("gh release create", source)
        self.assertIn("--prerelease --latest=false", source)
        self.assertIn("[publish-hacs-dev]", source)
        self.assertIn("if gh release view", source)

    def test_archive_is_reversible_explicit_and_never_deletes_tags(self):
        source = (ROOT / ".github/workflows/archive-obsolete-dev.yml").read_text()
        for tag in (5, 6, 7, 8, 9):
            self.assertIn(f"v0.16.12-dev.{tag}", source)
        for safe in ("v0.16.12-dev.10", "v0.16.12-dev.11", "v0.16.12"):
            self.assertIn(safe, source)
        self.assertIn("[archive-obsolete-dev]", source)
        self.assertIn("github.repository == 'chadakenn/Kellies-Voice'", source)
        self.assertIn("latest", source)
        self.assertIn("PATCH", source)
        self.assertIn("draft=true", source)
        self.assertNotIn("gh release delete", source)
        self.assertNotIn("DELETE", source)


if __name__ == "__main__":
    unittest.main()
