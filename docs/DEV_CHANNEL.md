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
  signed APK + SHA-256 metadata from a stable GitHub Release. **Unchanged**
  for anyone who never opens the new switch. The daily check runs only on
  Stable.
- **Development switch:** Settings → **Check for app updates** →
  **Development prereleases**, then **Check now**. It calls the public
  release list with no token and offers only a **published** prerelease
  whose tag looks like `v0.16.10-dev.1`, with the same `app-release.apk`
  and `update.json` rules as stable. Download and install still wait for
  a separate tap and Android's installer.
- **Actions artifacts are not an in-app update.** The signed
  `kellies-voice-dev-signed` artifact stays the way to install a build
  that has not been published. GitHub does not let an app download a
  private Actions artifact without a token, and this app must not contain
  one. Publishing a prerelease is a separate owner decision. This change
  does not publish one.
- **Rollback:** choose **Stable public releases** and check again. If the
  newest stable `versionCode` is lower than the installed development
  build, the app refuses it. Android cannot downgrade in place. The next
  stable release must use a higher `versionCode` than every dev build a
  tester installed. Do not uninstall to roll back unless the owner accepts
  losing the saved login.

## Owner checklist before anyone uses Development updates

1. Keep the dev workflow's signed artifact as the install path until you
   choose to publish. Do not mark a release as the latest stable.
2. To try the in-app path, publish a **prerelease** (not a draft) tagged
   `v` plus the `versionName`, for example `v0.16.10-dev.1`. Attach
   `app-release.apk` and `update.json` with `versionCode`, `versionName`,
   `tag`, and lowercase `sha256`. The tag and `versionName` must match
   aside from the leading `v`.
3. On a TV that already runs the signed dev build, open **Settings →
   Check for app updates → Development prereleases → Check now**. Confirm
   it does not appear for a TV left on Stable.
4. Download, then install, and accept Android's prompt. Confirm the
   package was not replaced by a different app.
5. Switch back to Stable and check again. The current stable release must
   be refused while its version code is lower. Record that result.
6. Before the next public stable release, set its `versionCode` above the
   highest development code anyone installed.

The public release list is unauthenticated and can be rate-limited. The
app reads only the newest 20 public releases, so the development
prerelease has to be in that page. A check is manual on the Development
switch. Nothing here starts the microphone or a background updater.

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
