# Stable releases and opt-in beta testing

**Public stable: [the latest signed release](https://github.com/chadakenn/Kellies-Voice/releases/latest).** As of November 1, 2026, the latest stable is **v0.17.3 (Android build 58)**. If you just want to use Kellie's Voice, stay on stable. You do **not** need to enable betas for signed updates.

**Philips volunteer candidate: v0.17.4-dev.1 (Android build 59).** This opt-in prerelease bundles smoother HA dashboard startup, `show_dashboard` / `hide_dashboard` HA actions and Philips OLED field-test guidance. The signed APK and Home Assistant integration must **both** be v0.17.4-dev.1. See the [volunteer test checklist](qa/v0.17.4-voravin-test.md) and [PR #300](https://github.com/chadakenn/Kellies-Voice/pull/300). This is not yet the public stable v0.17.4 release; TV behavior still needs field testing.

**Previous beta:** v0.16.13-dev.1 (Android build 53) was an opt-in prerelease between stable v0.16.12 and the now-published stable v0.16.14. Choose stable v0.16.14 when available. Historical v0.16.12-dev.N versions are for manual recovery/testing, not the newest beta channel.

## Private QA builds are different from the public beta channel

Older v0.17 private QA integration artifacts were **development builds**, not automatically delivered HACS pre-releases. The tested nonpublishing signed QA installer is **`app-qa.apk` (`com.example.tvvoiceprobe.qa`)**, which coexists with stable and has a distinct TV identity/login. It must come from the persistent-signing **Build Kellie's Voice Signed APK** workflow, with **Publish public release unchecked**. The PR-built `app-debug.apk` (`.debug`) has a different package/signing identity and is **not** an update for an already-installed signed QA app. The isolated `kellies_voice_v3qa` ZIP is not a replacement for the public `kellies_voice` HACS integration. Do not install a QA artifact as a stable update. See [v0.17 tester status and release gates](V017_BETA_STATUS.md) and the [hardware ADB captures](screenshots/PR-192-CAPTURES.md) (older preview UI, not final).

## Join the beta (optional)

Only testers who explicitly choose a development release should receive one. Beta features can break TV voice, overlays, reconnection, or Home Assistant integration, so keep a Home Assistant backup and share non-sensitive test observations on [GitHub Issues](https://github.com/chadakenn/Kellies-Voice/issues).

**Home Assistant (HACS):**

1. Open **HACS → Kellie's Voice** (or [open the repository in HA](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration)).
2. In recent HACS versions, enable the repository's **Pre-release** update switch if you want beta versions considered in *update checks*. That switch is initially disabled in Home Assistant, so you may have to enable its entity first. Leave it off to stay on stable.
3. For a **one-time manual beta install** in HACS, open **⋮ → Redownload → Need a different version?**, then choose the particular **v...-dev.N (pre-release)** under Release and **Download**. Some versions expose a **Show beta** control. The manual list may show beta tags even while automatic beta update checks are off.
4. Restart Home Assistant after the integration downloads. To leave beta, choose a later compatible stable release. Always check version compatibility before changing versions.

**TV app (separate download and preference):**

1. Stable is the default app update channel. Open **Kellie's Voice → SETTINGS → Updates & account → Update channel** and stay on **Stable** for normal use.
2. Testers can explicitly select **Development (opt-in)** before **Check**. The TV requests a published, signed prerelease; it does **not** install one automatically. Android asks you to approve its package install.
3. Install the **same-version signed APK** as the HACS integration wherever a feature requires matching Android and HA changes. HACS cannot install the TV APK, and the TV updater cannot install a HACS integration.
4. Return the update channel to **Stable** when testing ends. **Warning:** Android does not install an APK with a *lower* versionCode over a higher build. Public v0.16.12 is code **50**, older dev.11 is **51**, v0.16.13-dev.1 is **53**, and v0.16.14 is **54**. Do **not** install an older lower-code APK over a newer one. v0.16.14 supports upgrading over build 53 without clearing app data.

HACS and the TV have **separate** beta opt-in controls. Enabling one does not enable the other. Okay Nabu remains **OFF by default** even when installed as part of v0.16.14; enabling the listener requires an explicit TV choice and Android microphone permission.

## Release hygiene

- **Stable:** signed, tested releases are published as regular GitHub Releases and appear under [Latest](https://github.com/chadakenn/Kellies-Voice/releases/latest).
- **Routine development:** every `dev` push can run CI and upload a short-lived signed Android APK as a private **GitHub Actions artifact**, not a public GitHub Release. These artifacts are for maintainers and QA, not general HACS installation.
- **Public betas:** only a reviewed `dev` commit intentionally marked `[publish-hacs-dev]` can run the separate signing/prerelease workflow. Every beta is tagged `-dev.N`, marked **pre-release**, and is never set as GitHub Latest. Do not use that marker for ordinary commits.
- **Obsolete betas:** old `v0.16.12-dev.5` through `dev.9` are archived as **GitHub draft releases** when cleanup succeeds. This hides them from HACS and public release browsing but keeps their Git tags and release assets available to maintainers for recovery. To restore one, edit the draft release in GitHub and republish it as a *pre-release*. Do not reuse those tags or delete the signing history. The older dev.10 and newest dev.11 remain available for opt-in testers until superseded.

The archiving workflow under `.github/workflows/archive-obsolete-dev.yml` runs **only once on the explicitly marked housekeeping merge**, validates that public Latest is still stable v0.16.12, preserves dev.10 and dev.11, and acts on an exact five-tag allowlist. It never edits a stable release or deletes assets. If GitHub denies updating a release because its tag changes workflow files, the job reports an error rather than trying to delete tags or bypass permissions. A maintainer with GitHub release edit permissions can then manually change those old releases to **Draft**.

After housekeeping, a signed beta that is newer than stable is the only one eligible for normal opt-in HACS update checks; manual old-version selection is a separate GitHub/HACS capability. Check the actual release list before claiming cleanup complete.

## Where to report results

Open a [new Kellie's Voice issue](https://github.com/chadakenn/Kellies-Voice/issues/new/choose). Include your Android TV model and version, app/HACS version, exact repro steps, and what appeared onscreen. Never attach a token, pairing ID, camera URL, private camera view or signing keys.
