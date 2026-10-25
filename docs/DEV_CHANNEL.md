# Kellie's Voice development and testing lane

The public repository's default branch is **main**. **dev** is a staging branch
for pre-release QA on the owner's onn 4K Pro. Changes under development must
not appear in public GitHub Releases until approved.

## Branch policy

| Branch | Purpose | May publish a public stable release? |
| --- | --- | --- |
| `main` | Released, reviewed, tested code; remains default | Only with explicit owner approval and existing guarded release workflow |
| `dev` | Consolidated candidate fixes and feature tests | **No** |
| `fix/*`, `feat/*`, `docs/*` | Narrow individual changes (including Grok work) | **No** |

Propose changes by PR into **dev**; do not push a draft/experimental feature
directly to `main`. Grok and ChatGPT should use different feature branches.
PRs can target `main` only when the owner approves promotion from tested dev.

## Build and install an Android dev APK

1. Merge an approved test change into `dev`. The
   [Build Kellie's Voice Dev APK](../.github/workflows/dev-apk.yml)
   GitHub Actions workflow starts automatically on app/build-related pushes.
   To rerun manually, open **Actions → Build Kellie's Voice Dev APK → Run workflow**
   and select branch **dev**. This workflow requires the existing repository
   signing secrets and runs Android plus HA Python tests before signing.
2. Open the successful dev workflow run; download the artifact
   **`kellies-voice-dev-signed`** and extract **`app-release.apk`**.
   This is a test artifact, **not** a public GitHub Release.
3. On your TV, install **over** the installed signed v0.16.9.
   The package ID and private signing identity are the same; do not uninstall
   or clear storage. Android still requires the user's confirmation.
   If Android reports an incompatible certificate or downgrade, **stop**.
4. In the app check the distinct development `versionName`, then test
   voice, alert receiver, cameras, timers and reconnect behavior.
5. Record the branch commit, Android version, Home Assistant integration
   version and actual hardware pass/fail in the related GitHub PR or issue.
   **CI is not a substitute for real TV testing.**

For **every later installable dev APK** increment `versionCode` in
`app/build.gradle` above the current installed build. Dev begins at
`versionCode 27`, `versionName 0.16.10-dev.1`, compared with stable
v0.16.9 code 26. Before promoting a public release, set stable's
`versionCode` **above the largest dev code installed by testers**.
Android will not accept a lower code as an in-place upgrade. The same
package ID means dev and stable are **not** side-by-side apps. Keep the
versionName visibly suffixed with `-dev` while on dev.

## Home Assistant test integration

Android and HACS are different deliveries. The Android dev artifact does
**not** upgrade Home Assistant. For changes that require unreleased HACS
integration code, set up a **separate HA test instance** where possible, or
back up your existing `custom_components/kellies_voice` and install the
exact tested dev contents there, then restart HA and test. Do not assume
the normal HACS update mechanism automatically tracks the `dev` branch.
The dedicated HACS validation workflow runs for pushed `dev` changes.

Avoid running a test and stable Android alert receiver simultaneously with
the same TV pairing ID. Never upload HA tokens, signing secrets or private
camera URLs to GitHub.

## Normal vs experimental updates

- **Stable TV updater:** still reads `/releases/latest` and requires the
  signed APK + SHA-256 metadata from a stable GitHub Release. **Unchanged**.
- **Dev updates:** currently delivered by the **signed Actions artifact**
  above, requiring an explicit install. There is **no in-app dev-channel
  switch yet**. A future optional dev-update channel must preserve SHA-256,
  certificate/package verification and owner installation consent.
- The existing `build-apk.yml` public release job is gated to **main**.
  The dev workflow has read-only GitHub permissions and **no publish job**.

## Recommended test gate

1. ChatGPT implements/reviews core behavior on a feature branch, and Grok
   independently reviews UX, documentation, regressions or a separate task.
2. CI passes. Approve PR into `dev`, then build/install signed dev APK.
3. Test actual TV behavior, record pass/fail, fix on feature branch as needed.
4. Only after owner approval, promote the tested changes to `main`,
   advance stable version, and make a public release through the guarded flow.

This does not promise unlimited background receiver uptime. Android TV
can stop background processes; genuine permission denial and invalid tokens
must remain visible and must never be bypassed.
