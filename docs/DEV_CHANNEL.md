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

### v0.16.10-dev.2: repeated HA startup authorization test

On the owner's onn 4K Pro, v0.16.10-dev.1 still showed **TV alerts:
permission denied** after Home Assistant restarted. The corresponding HA
TV alert receiver sensor stayed OFF with `last_seen_since_ha_start: never`
for over four minutes. The five fast authorization rechecks had expired,
leaving manual Resume as the only recovery path.

The next candidate, **v0.16.10-dev.2 / versionCode 28**, continues a
low-frequency recheck every two minutes after the initial five tries.
Each attempt still goes through Home Assistant's normal read permission
verification; the app does not receive or replay events while access is
denied. `auth_invalid` and unsupported HACS errors are still terminal.
This is **unverified on real hardware** until tested after installing the
new signed APK.

**Hardware acceptance:** install v0.16.10-dev.2 over dev.1 without
uninstalling, verify the version, start with the HA alert receiver ON,
restart HA and **do not press Resume**. Watch the TV alert status and
the HA `TV alert receiver connected` binary sensor until it comes back
ON by itself, then send a fresh announcement and check that the TV shows
it. Test real account permission denial separately: the app must never
show Connected or accept announcements without HA approving the scoped
subscription. If this remains disconnected for more than 10 minutes,
capture the non-sensitive TV status and report it without exposing tokens.

### v0.16.10-dev.3: watchdog and TV diagnostics

Dev.2 was confirmed installed on the onn 4K Pro. After restarting
Home Assistant, the alert receiver remained OFF (last seen: never) for
about nine minutes, despite an available HA TV alert position control.
The owner pressed **TV alert status → Resume TV alerts** and the receiver
immediately connected. A subsequent test announcement appeared on screen.
This proves a manual connection and delivery work; **the precise automatic
retry failure is still under investigation**.

Dev.3 (versionCode 29) adds an independent recovery monitor that checks
for an overdue retry even when the ordinary socket watchdog is idle.
It respects explicit alert opt-out, absent overlay permission, missing login,
the scheduled retry deadline, and terminal authentication errors.
It also displays safe connection details in **Settings → TV alert status →
Last receiver activity**, such as:
- Opening alert socket, attempt N
- Scoped unauthorized, retry N in X seconds
- TV controls not ready
- Recovery watchdog repairing an overdue retry
- Authorized alert subscription on attempt N

The diagnostic **never prints saved credentials, IP addresses, access
tokens, pairing IDs, or raw server exception messages**. It remains on-device.

**Repeatable dev.3 test:** confirm installed version 0.16.10-dev.3 in
Android app settings, verify HA sensor ON, restart Home Assistant, leave
Kellie's Voice running and **do not press Resume**. Open TV alert status
only to read Last receiver activity, then compare it with the HA alert
receiver binary sensor. Record the diagnostic text after 2 and 5 minutes.
If it reconnects, send a fresh targeted announcement. If it does not,
capture the safe diagnostic message before manually resuming. No change
to the public release is authorized by this dev test.

### v0.16.10-dev.4: HA startup scoped-command registration race

The owner installed and tested dev.3 on an onn 4K Pro. After restarting
Home Assistant, the TV alerts receiver stayed OFF, and the new local
TV alert status diagnostic reported exactly:
**"Legacy event subscriptions denied."** The existing user then pressed
Resume TV alerts and the receiver connected immediately; a targeted test
announcement had already been confirmed visible during dev.2 testing.

This narrows the defect to the startup compatibility fallback. The TV
authenticates before Home Assistant has registered the custom
`kellies_voice/subscribe_alerts` WebSocket command, gets `unknown_command`,
immediately tries older `subscribe_events` event subscriptions, and the
non-admin user is denied. Dev.3 treated that legacy denial as a permanent
permission failure and stopped all automatic retries.

Dev.4 (versionCode 30) **prefers the modern scoped command**, retrying
`unknown_command` at 2, 4, 8, 16, and 30 seconds. It tries a legacy
subscription only once if the modern command remains unavailable after
startup. A denied legacy fallback now reconnects to retry the modern
command every two minutes, instead of setting a terminal denial. A
genuinely older HACS installation can still use legacy event access
where its configured account has permission. Every modern subscription
still requires normal HA authorization. Invalid credentials and explicitly
terminal permission errors remain terminal.

**Device acceptance test:** install signed dev.4 over dev.3 without
uninstalling, verify Android reports 0.16.10-dev.4, verify the HA receiver
ON, restart HA, and **do not press Resume TV alerts**. Watch the HA
`TV alert receiver connected` sensor and the safe **Last receiver
activity** line in Settings → TV alert status. The receiver should go
back ON without intervention. Send a new announcement to verify delivery.
If it does not, capture that last activity diagnostic before any manual
Resume, without exposing the HA token. CI alone does not prove hardware
recovery.

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
