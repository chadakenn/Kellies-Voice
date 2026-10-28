"""Guard opt-in beta update precedence and reversible release pruning."""

import json
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
    def test_current_version_is_newer_than_previous_stable_and_matches_hacs(self):
        build = (ROOT / "app/build.gradle").read_text()
        manifest = json.loads(
            (ROOT / "custom_components/kellies_voice/manifest.json").read_text()
        )
        name = re.search(r"\\bversionName\\s+'([^']+)'", build)
        code = re.search(r"\\bversionCode\\s+(\\d+)", build)
        self.assertIsNotNone(name)
        self.assertIsNotNone(code)
        version = name.group(1)
        self.assertEqual(version, manifest["version"])
        self.assertGreater(core(version), core("v0.16.12"))
        self.assertGreater(int(code.group(1)), 50)
        # Stable release notes must exist for the exact signed/HACS version.
        # Opt-in development versions are tested separately by the beta workflow.
        if "-dev." not in version:
            self.assertTrue((ROOT / "docs/releases" / f"v{version}.md").exists())
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
