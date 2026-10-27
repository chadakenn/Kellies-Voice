# Stable releases and opt-in beta testing

**Public stable: [v0.16.12](https://github.com/chadakenn/Kellies-Voice/releases/tag/v0.16.12).** Both the signed Android TV APK (build 50) and Home Assistant HACS integration are stable v0.16.12. If you just want to use Kellie's Voice, stay on stable. You do **not** need to enable betas to get stable updates.

**Next opt-in beta candidate:** **v0.16.13-dev.1 (Android build 53)**, which is *newer* than stable v0.16.12 for HACS prerelease update ordering. Old v0.16.12-dev.N builds have a lower prerelease precedence than stable v0.16.12, so they are historical manual-selection builds, not the new beta channel. Only use v0.16.13-dev.1 once its signed GitHub prerelease and both APK/HACS versions have actually been published and verified.

## Join the beta (optional)

Only testers who explicitly choose a development release should receive one. Beta features can break TV voice, overlays, reconnection, or Home Assistant integration, so keep a Home Assistant backup and share non-sensitive test observations on [GitHub Issues](https://github.com/chadakenn/Kellies-Voice/issues).

**Home Assistant (HACS):**

1. Open **HACS → Kellie's Voice** (or [open the repository in HA](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration)).
2. In recent HACS versions, enable the repository's **Pre-release** update switch if you want beta versions considered in *update checks*. That switch is initially disabled in Home Assistant, so you may have to enable its entity first. Leave it off to stay on stable.
3. For a **one-time manual beta install** in HACS, open **⋮ → Redownload → Need a different version?**, then choose the particular **v...-dev.N (pre-release)** under Release and **Download**. Some versions expose a **Show beta** control. The manual list may show beta tags even while automatic beta update checks are off.
4. Restart Home Assistant after the integration downloads. To leave beta, choose a later compatible stable release. Always check version compatibility before changing versions.

**TV app (separate download and preference):**

1. Stable is the default app update channel. Open **Kellie's Voice → SETTINGS → Check for app updates** and stay on **Stable** for normal use.
2. Testers can explicitly select **Development (opt-in)** before **Check**. The TV requests a published, signed prerelease; it does **not** install one automatically. Android asks you to approve its package install.
3. Install the **same-version signed APK** as the HACS integration wherever a feature requires matching Android and HA changes. HACS cannot install the TV APK, and the TV updater cannot install a HACS integration.
4. Return the update channel to **Stable** when testing ends. **Warning:** Android does not install an APK with a *lower* versionCode over a higher build. Public v0.16.12 is code **50**, older dev.11 is **51**, and the next v0.16.13-dev.1 beta is code **53**. Do **not** install build 53 on your only stable TV expecting to roll back in-place to build 50. Wait for a future stable code greater than 53, or use a dedicated beta TV.

HACS and the TV have **separate** beta opt-in controls. Enabling one does not enable the other. The beta feature is not an invitation to turn on microphones or any experimental wake word: wake listening needs additional explicit TV consent and is not part of stable v0.16.12.

## Release hygiene

- **Stable:** signed, tested releases are published as regular GitHub Releases and appear under [Latest](https://github.com/chadakenn/Kellies-Voice/releases/latest).
- **Routine development:** every `dev` push can run CI and upload a short-lived signed Android APK as a private **GitHub Actions artifact**, not a public GitHub Release. These artifacts are for maintainers and QA, not general HACS installation.
- **Public betas:** only a reviewed `dev` commit intentionally marked `[publish-hacs-dev]` can run the separate signing/prerelease workflow. Every beta is tagged `-dev.N`, marked **pre-release**, and is never set as GitHub Latest. Do not use that marker for ordinary commits.
- **Obsolete betas:** old `v0.16.12-dev.5` through `dev.9` are archived as **GitHub draft releases** when cleanup succeeds. This hides them from HACS and public release browsing but keeps their Git tags and release assets available to maintainers for recovery. To restore one, edit the draft release in GitHub and republish it as a *pre-release*. Do not reuse those tags or delete the signing history. The older dev.10 and newest dev.11 remain available for opt-in testers until superseded.

The archiving workflow under `.github/workflows/archive-obsolete-dev.yml` runs **only once on the explicitly marked housekeeping merge**, validates that public Latest is still stable v0.16.12, preserves dev.10 and dev.11, and acts on an exact five-tag allowlist. It never edits a stable release or deletes assets. If GitHub denies updating a release because its tag changes workflow files, the job reports an error rather than trying to delete tags or bypass permissions. A maintainer with GitHub release edit permissions can then manually change those old releases to **Draft**.

After housekeeping, a signed beta that is newer than stable is the only one eligible for normal opt-in HACS update checks; manual old-version selection is a separate GitHub/HACS capability. Check the actual release list before claiming cleanup complete.

## Where to report results

Open a [new Kellie's Voice issue](https://github.com/chadakenn/Kellies-Voice/issues/new/choose). Include your Android TV model and version, app/HACS version, exact repro steps, and what appeared onscreen. Never attach a token, pairing ID, camera URL, private camera view or signing keys.
