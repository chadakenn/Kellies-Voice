# Kellie's Voice development and testing lane

## v0.16.12-dev.6 / build 45: Home Assistant camera dropdowns

Owner reported the single HA `Frigate HA camera entities` text control was confusing. New HA **Frigate snapshot camera 1–4** selects automatically discover existing HA `camera.*` entities, present their friendly names with IDs, and write a validated, de-duplicated, ordered list through the existing paired TV text control. Changes to the legacy text entity automatically populate the dropdowns, and the text entity restores saved choices after HA restart. Invalid/duplicate IDs and skipping earlier slots are rejected. These dropdowns only affect the snapshot-grid mode; clear the Frigate web URL to use them. The Android build 44 protocol works with the new HA integration, but a signed build 45 keeps the matched beta release version. This needs owner testing before stable main.

## HACS dev prerelease picker

HACS does **not** list arbitrary repository branches in its ordinary **Redownload → Need a different version?** picker. HACS lists recent published GitHub Releases and the default branch. The repo's `dev` branch is already directly installable without any release: Home Assistant → **Developer Tools → Actions** → `update.install` targeting **Kellie's Voice's HACS repository update entity**, with `version: dev`. Do not target the HACS application's own update entity. After download, restart Home Assistant.

For a **visible beta option** (after owner approval), the `dev` branch has a dedicated [signed dev prerelease workflow](../.github/workflows/publish-hacs-beta.yml). Only a reviewed `dev` push with the explicit `[publish-hacs-dev]` commit marker may publish. Ordinary dev merges do not create a public prerelease. It verifies the Android `versionName` matches the HACS integration manifest, reruns Android/HA tests, signs with the existing keystore, checks signature, creates SHA-256 `update.json`, and publishes a GitHub **prerelease** (never GitHub Latest) tagged `v0.16.12-dev.5` or the actual next bumped dev version. The prerelease includes the signed Android APK so the TV's deliberately opt-in Development channel can also consume it. It never updates stable `main`.

After publication: **HACS → Kellie's Voice → ⋮ → Redownload → Need a different version? → Show beta versions → `v0.16.12-dev.5` → Download**. Beta choices may require refreshing HACS metadata. Restart HA after the download. When back on stable, choose public `v0.16.11` and restart HA; maintain Android matching versions and don't downgrade an Android `versionCode` by uninstalling.


## v0.16.12-dev.5 / build 44: Home Assistant Frigate controls and remote navigation

HA adds paired per-TV screensaver ON/OFF, URL, camera IDs, size, desktop layout and fit entities. Android consumes validated changes from a dedicated saved-login WebSocket even while the voice activity is closed, provided Home Assistant layout was explicitly enabled on the TV. The OFF switch does not destroy the settings channel, so a later ON can reactivate it. The Frigate web page adds a D-pad-driven selection cursor and OK-to-tap camera navigation. "Fit more cameras" widens the responsive viewport to 1920 CSS pixels for a less cropped view, with 1440/1200 fallbacks. Requires matching Android and HACS dev.5 and owner device testing before stable release.

## v0.16.12-dev.4 / build 43: Google TV ambient watcher recovery and diagnostics

Re-enable the already-opted-in Frigate watcher after app updates/reboot (including when TV alert notifications are disabled), return `START_STICKY`, and show safe, in-process Diagnostics for service alive, overlay permission, Frigate config, last system dream event and overlay result. Adds **Test Frigate as TV overlay** independent of Google Ambient idle detection, to distinguish successful rendering from missing dream broadcasts. Android's Google TV Ambient Mode **Source** is not a third-party dream picker. Do not claim automatic Ambient Mode support until on-device QA confirms receipt of DREAMING_STARTED and a visible overlay. Signed build 43, app and HACS `0.16.12-dev.4`; does not alter existing HA or Frigate setup.

## v0.16.12-dev.3 / build 42: TV-size Frigate browser rendering

A photo comparison found the Frigate webpage using a giant single-camera mobile layout on the onn TV while desktop Chrome on the laptop showed a live camera grid. The new **Frigate page layout → Desktop TV** (default, with **Automatic** fallback) requests a desktop Chromium user-agent based on the installed WebView, enables wide viewport and overview fit, then applies a wide CSS viewport on page load. Frigate page mode uses full-height WebView behind an overlay Close button. The authenticated Home Assistant snapshot grid is unchanged. Configure **Dashboard size → Large**, preview, verify actual camera streams and Back / Close on hardware. Signed development code **42** upgrades build 41 without uninstalling, and the matching HACS development manifest version is `0.16.12-dev.3`. This is still **not a public release**. Frigate group layouts are browser-local and might need initial TV arrangement.

## v0.16.12-dev.2 / build 41: Frigate screensaver configuration UI repair

The previous signed dev.1 build had Frigate ON/OFF and Preview but no way to enter its URL or camera IDs. Dev.2 adds a dedicated **Settings → Frigate screensaver** menu with a validated local Frigate URL field, up to four real Home Assistant `camera.*` IDs, dashboard size, Android system screensaver chooser, and Preview. Clearing the URL selects the camera grid. Credentials and secret-bearing URLs are rejected. The matched HACS dev manifest is `0.16.12-dev.2` (no HACS behavior changes); signed Android versionCode **41** upgrades dev.1 code 40 without uninstalling or clearing pairing. **Owner real-TV QA still required**, do not release to stable `main` yet. See [Frigate setup](FRIGATE_SCREENSAVER.md) and [QA](TESTING.md).


## Combined one-install QA candidate: v0.16.12-dev.1 / build 40 (2026-10-25)

**Stable main remains v0.16.11 (Android build 39).** This development candidate combines two separately CI-reviewed improvements:

- **Grok PR #119**, ported from #87: OFF-by-default, selectable `Kellie's Voice Frigate` Android screensaver, local preview and safe camera snapshots or Frigate page. On some TV firmware the built-in Google screensaver blocks ordinary overlays: use Android **Settings → Display → Screensaver → Kellie's Voice Frigate** when available, rather than assuming an overlay will appear on Google's own screensaver. The microphone must never be opened.
- **ChatGPT PR #120**, ported from #61: optional up to three D-pad focusable popup buttons. HA can send bounded `actions` in `kellies_voice.announce`, still camera or live camera. One authenticated, per-TV, expiring, one-use HA button acknowledgment event; no direct lock or garage service invocation. Plain popups without buttons must remain passive and cannot steal remote focus.

**Build/installation:** After the dev branch CI succeeds, download `kellies-voice-dev-signed` artifact and install signed Android `v0.16.12-dev.1` **versionCode 40** over signed v0.16.11 code 39 or dev.7 code 38. Do not uninstall or clear pairing. These new scoped buttons require the matching **development HACS integration manifest 0.16.12-dev.1**, installed from the dev repository with a deliberate Home Assistant restart; the Frigate screensaver itself is Android-only. The developer must NOT overwrite public stable HACS unexpectedly, and the user must approve their test installation.

**QA in one sitting:** (1) existing TALK, follow-up/mic STOP, 40% spoken announcement and TV alerts; (2) Frigate screensaver OFF unchanged, then ON → preview → Android actual screensaver selection → idle launch → Close/Back/return to video, without the microphone; (3) one harmless acknowledge button on text alert and camera snapshot/live, press OK once, verify one HA `kellies_voice_announcement_action` event; Back and timeout must emit zero; (4) ensure all Settings rows including the new 21-item menu open correctly; (5) after HA restart, verify receiver reconnect and normal voice camera scripts. **No dangerous button actions during testing.**

New features are NOT hardware verified merely because CI succeeds. Do not publish to main or release until owner and other tester QA passes.
[Action-button instructions](TV_ACTION_BUTTONS.md) · [Frigate screensaver guide](FRIGATE_SCREENSAVER.md).



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

## Pending batch: 48 kHz microphone fallback

Development-only port of PR #6. The Assist capture path first tries the
unchanged 16 kHz `VOICE_RECOGNITION` input (the known-working onn 4K Pro
route). If that rate cannot be initialized, it tries 48 kHz. The new
`PcmCapture` anti-aliases and converts 48 kHz mono PCM16 to the same
16 kHz frames, so STT and the VAD do not need a different streaming mode.
Both physical rates are rejected if neither can be opened.

The existing microphone-blocking preference, route verification and
explicit user-initiated TALK gating remain in force. This is **not**
a bypass of hardware microphone permissions or a promise that a
Google TV remote's Google-only microphone becomes usable.

**Batch QA** after the next signed dev APK: onn 4K Pro normal TALK and
transcript should remain unchanged (16 kHz first). Only test the 48 kHz
fallback on a TV with an actual allowed microphone that fails at 16 kHz.
No production APK installation or public release is triggered by this
development change alone.

## v0.16.11-dev.7: fix blocked follow-up settings path

A real onn 4K Pro user installed v0.16.11-dev.6 (build 37) and selected
**Settings → Follow-up listening**. The TV instead displayed the
**Microphone Routing** menu. Root cause: the entries array has follow-up
at index 5 and mic input detection at index 6, but their click handlers
were reversed.

Android v0.16.11-dev.7 (build 38) swaps the two handlers back to their
correct positions, without changing either feature's behavior or
microphone permission preferences. A settings mapping regression test
checks every menu row against its action, preventing accidental future
index shifts during feature additions. No HACS component change is needed.

Hardware acceptance: install the signed dev.7 app over build 37,
do not uninstall or clear settings; open **Follow-up listening** and
verify it shows Off / On (5, 8, 10, 15 seconds), select **On, 8 seconds**,
then return to Settings and confirm the label is ON, 8 seconds. The
separate **Mic input detection** row must continue to open Microphone
Routing. Then test the second conversational question after a spoken
reply, and STOP/timeout/privacy. Issue #43 stays open until the owner
passes real hardware tests. Stable `main` unchanged.

## Single-install batch: v0.16.11-dev.6 / build 37

This batch is staged specifically because the owner requested **one
consolidated test install**, not repeated APK downloads. It includes:

- Independent per-TV spoken alert gain presets (20/40/60/80/100%; default 60%)
  with Android TextToSpeech utterance-level gain only; no system volume changes
- 16 kHz-first microphone capture, 48 kHz fallback with PCM downsampling
- Opt-in follow-up questions after spoken replies (5/8/10/15s), with run-scoped
  ownership of a preview microphone and explicit capture-release/privacy rules
- HACS-only manual pairing confirmation for mistyped pairing UUIDs
- HACS-only experimental native timer handler OFF by default, with ID-scoped
  TV timers and pause/resume handling (requires native HA Assist intents)

**Rollout/test requirements:** install only the one signed dev.6 code37 APK
over existing code35 (do not uninstall or clear data). No public release.
The matching development HACS component is version 0.16.11-dev.6. Updating
HACS changes the live Home Assistant installation; take a configuration backup,
update the correct Kellie's Voice integration to the `dev` branch and restart
Home Assistant **only when explicitly approved for a test session**.

Recommended QA order: confirm volume 40% and a local spoken test; with follow-up
OFF confirm one question; turn ON/8s and ask a second contextual question;
confirm STOP/timeout turn off mic only after capture is released; check baseline
TALK/microphone and camera events; then test HACS manual-pairing warning and
experimental native timer via an HA native-intent-capable pipeline.
The Ollama LLM may not route a phrase to native timers, even when the handler
is enabled. Do not call #43, #57, #69 closed until actual acceptance evidence.
The already-confirmed #44 reboot-readiness issue was closed at owner direction.

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

### #69 targeted TV delivery feedback (experimental HACS fix)

The v0.16.10 public release can accept HA events successfully even if the
requested *configured* pairing ID is mistyped and no real TV subscribes to it.
The existing scoped `TV alert receiver connected` diagnostic can show that
a mispaired TV has never connected, but that status is not reflected in the
service result.

This development change introduces a preflight for **explicitly targeted**
`announce`, camera snapshot/live/hide, forecast and timer actions:

- When at least one modern scoped receiver has connected since HA Core started,
  requests targeting a *disconnected* TV raise an actionable service error
  **before publishing** rather than appearing successful. No target ID or
  access token is included in the error.
- Normal explicitly targeted requests to a currently connected TV still publish
  exactly the same event, without changing overlay rendering or permission.
- Omitted `device_id` broadcasts remain unchanged and best-effort.
- When no scoped receiver has ever been observed during this HA session, an
  older Android receiver may be in use. To preserve legacy behavior, the action
  continues but HA logs a safe generic warning that delivery is unverified.
  This deliberate compatibility concession means not all incorrect IDs can
  be rejected. It is **not a receipt** proving an on-screen popup.

Before promotion, test with two HA configurations (one real subscribed TV,
one plausible but incorrect manually paired ID). Verify targeted mistaken ID
fails in Developer Tools Actions; real ID succeeds with visible popup; omitted
ID broadcasts as before; and an intentionally legacy-only client still works.
No change to stable main is included in this PR.

### #44 voice startup readiness candidate (development only)

The device can establish a Home Assistant WebSocket after Android has started,
while Assist pipelines may take longer to become available. This patch gives
separate local, redacted messages for voice connection warmup, an unavailable
Assist pipeline, STT, conversation-agent and TTS failures.

An explicitly launched overlay still holds a pending user request for
**at most 30 seconds**; after expiry, it requires TALK again. Full-screen
TALK when disconnected is not queued and never triggers later recording.
Invalid credentials remain non-retryable. Existing websocket retry backoff
continues unchanged; the patch starts no new background microphone.

Unit tests check 30-second expiry and error classification. Hardware acceptance
requires immediate TALK on an onn 4K Pro after reboot/HA restart, verifying
Connected/Ready transitions, safe expiry without surprise recording, and
unaffected normal listening and TV alerts. Do not merge or publicly release
until on-device tests pass.

### #71 Philips TV phantom microphone: manual no-microphone mode (dev-only)

Community tester @voravin measured two Philips TVs where the Android
`AudioManager.getDevices(GET_DEVICES_INPUTS)` list includes a phantom
`TYPE_BUILTIN_MIC` (id 16) despite no actual app-accessible microphone.
Another reported input type 26 appears to map to Echo Reference In on a
Philips set. One TV produces all-zero PCM, another produces a nearly
constant high-amplitude signal, so neither device-type allowlisting nor a
simple silence threshold reliably establishes live speech capture.

This targeted candidate adds a reversible, **device-local manual opt-out**
under **Settings → Mic input detection → Disable microphone capture on this
TV**. When set, TALK fails with a clear message *before* sending an Assist
pipeline request or opening AudioRecord. The check is also repeated just
before the capture thread opens AudioRecord to protect a mid-session settings
change. This opt-out takes priority over the separate "Try unverified
microphone" compatibility override and does not affect other TVs. It is
off by default. A user can re-enable later, such as after adding a USB mic.

**This is a workaround, not an automatic detection fix.** Keep #71 open and
request a Philips/no-USB physical test confirming TALK refuses capture,
a re-enabled USB microphone test, and onn 4K Pro normal mic regression.
No tokens, audio files or personal device IDs should be included in reports.
No stable release is authorized by this development candidate.

### HACS voice-relay dev branch installation (explicit owner approval only)

The scoped alert forwarding code in `dev` now preserves an opt-in,
validated `speak` sentence. For a non-admin TV user, the stable v0.16.10
HACS integration strips this field, so Android receives only the visual
popup even though the local spoken self-test passes.

The development integration's `manifest.json` is set to
`0.16.11-dev.4` to make the installed code identifiable, matching the
reviewed Android dev.4 build. This **does not publish a GitHub Release**.

For a *separately approved* live HACS test, first take a Home Assistant
backup. The tracked HACS `update` entity can install a **specific public
branch** using `update.install` and `version: dev`, rather than
the default stable release. Confirm the repository update entity belongs
to Kellie's Voice, and do not use the generic HACS integration update.
Then restart Home Assistant after the update, verify the receiver connects,
and use a single device-targeted spoken `announce` action while the TV
setting is enabled. Never send the test sentence to a different TV.
Rollback can choose stable `v0.16.10` via the same entity followed
by a Home Assistant restart, if necessary.

A dev HACS installation affects the user's live Home Assistant instance.
Do not perform that change or restart without explicit authorization.
Do not change `main` or publish a public release.

### v0.16.11-dev.5: independent spoken-announcement volume

On the owner's onn 4K Pro, end-to-end spoken `kellies_voice.announce`
succeeded using Android dev.4 and HACS `dev`, but the owner reported
that the voice was too loud.

Dev.5 (`versionCode 36`) adds **Settings → Announcement voice volume**
with 20%, 40%, 60%, 80%, 100% options. Fresh installations default to
**60%**; existing installations also use 60% until the owner selects
something else. This is **relative Android TTS utterance gain**, not
a change to the TV or media app volume. It affects the local speech
self-test and spoken TV alerts/camera announcements, but **does not**
change Assistant reply playback from HA, timer chimes, or YouTube volume.
The gain is passed as `TextToSpeech.Engine.KEY_PARAM_VOLUME` on each
utterance; Android's TTS API permits a 0.0–1.0 per-utterance float,
although final perceived volume depends on the installed TTS engine.

**Hardware QA:** Install the signed dev.5 APK over dev.4, preserving data.
Open TV app Settings and verify **Announcement voice volume: 60%**.
Choose 20% or 40%, then use **Test spoken announcements locally**.
Verify that speech is less loud than at 100%, changing the setting persists
after leaving Settings, and visual-only alerts remain silent. Confirm
spoken Home Assistant alerts use the same chosen level. Do not retest
YouTube ducking until the owner is in a position to check it, and do not
promote to stable without approval.

### v0.16.11-dev.4: scoped speech transport repair + local TV speech test

The owner installed v0.16.11-dev.3 on an onn 4K Pro and checked **TV alert
status**. The receiver was connected, but **Last spoken alert** still reported
"No spoken alert attempted in this app session." Source review established that
`alert_transport.EVENT_FIELDS` on the *running* HACS integration does not
include the new optional `speak` key and therefore strips it before forwarding
scoped events. A visible text popup does not prove speech reached the Android app.

This candidate updates the **development HACS integration** sanitizer to
retain only short, validated `speak` strings for announcement, snapshot, and
live camera events. Unsafe URLs, oversized messages, and unexpected extra
fields still never reach the scoped client. Plain visual alerts remain silent
by default and no secret fields are forwarded.

**Separate local test for installed Android dev.4 / code 35:**

1. Install the signed dev.4 APK over dev.3 (do NOT uninstall or erase data).
2. In the TV app, leave **TV announcements & cameras ON** and **Spoken
   announcements ON**.
3. Open **Settings → Test spoken announcements locally**. This executes a
   **local** event, without using the HACS WebSocket relay or network.
4. If you hear voice, the Android TTS engine works; if silent, immediately
   reopen **Settings → TV alert status** and report **Last spoken alert**.
5. Once local TTS is confirmed, test real HA `speak:` messages only after
   a matching, backed-up **development HACS integration** is explicitly
   approved and installed. Public stable HACS still drops this field.

Do not change the production HA custom component automatically or claim an
end-to-end spoken alert pass based on the local test alone. No main merge,
public Release or HACS stable publication is authorized.

### v0.16.11-dev.3: Android TTS engine discovery correction

On the maintainer's onn 4K Pro, **v0.16.11-dev.2 displayed a raw,
targeted spoken announcement but produced no audible speech** even with
Spoken announcements ON. This is **FAILED physical acceptance**, not a
shipping success. Dev.3 / `versionCode 34` adds the Android 11+ package
visibility declaration for `android.intent.action.TTS_SERVICE`, required
for apps targeting SDK 30+ to find the device's installed Android TTS
engine. The Android SDK target is 35.

Dev.3 also shows **Last spoken alert** under TV app → Settings →
**TV alert status**, with fixed non-sensitive messages for initialization,
engine errors, submitted speech, and completed speech. These status
messages do not prove audio was actually audible and do not contain
spoken text, Home Assistant credentials, addresses, or pairing IDs.

**Single hardware retest:** install signed dev.3 over dev.2 without
uninstalling; retain Spoken announcements ON; play YouTube and send a
device-targeted `kellies_voice_announcement` event with both `text`
and `speak`. Check audio duck, spoken message, and volume restoration.
If still silent, open TV alert status and report **Last spoken alert**
exactly; check that Android TV has a usable TTS engine configured.
Do not mark the feature passed without audible speech. Stable main
and the installed Home Assistant HACS integration remain unchanged.

### v0.16.11-dev.2: optional spoken announcements with camera speech

This development-only candidate combines reviewed spoken announcements (PR #104)
with the already-integrated startup, microphone and TV receiver fixes from dev.
Signed APK: **versionCode 33**, `versionName 0.16.11-dev.2` (above
dev.1 / code 32 and stable code 31). No public release is published.

**App setting:** TV app → Settings → Spoken announcements (OFF by default).
With a matching development Home Assistant integration, `kellies_voice.announce`,
`show_camera`, and `show_live_camera` accept an optional short `speak` string.
The camera action speaks while its own popup stays visible; existing normal
visual alerts remain silent. TV media playback should duck during the speech
and return to its original level when speech completes or stops.

**Hardware acceptance before any stable promotion:** check installed version,
keep YouTube playing, enable spoken announcements, trigger spoken and silent
alerts, dismiss a spoken alert early, send overlapping alerts, test speaking
with live Frigate popups and confirm the media focus is restored. Test
microphone/TALK, timer chimes, no mic activation, and reconnect. Disable
spoken announcements and ensure existing visual cards continue to work.

**Important:** Android dev APK alone does not update HACS. The current public
HACS services do not provide the new `speak` field. To test the service API,
use a separate HA test instance or an explicitly approved backed-up dev
integration. Never overwrite a production HACS install without approval.
The dev signed artifact is a GitHub Actions ZIP, not an in-app update; do not
publish a public prerelease or stable release as part of this test.

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
