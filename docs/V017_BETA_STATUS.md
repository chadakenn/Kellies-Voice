# Kellie's Voice v0.17.0: tester guide and release-readiness status

> **Historical QA record, October 31, 2026.** The signed public v0.17.0 release must be downloaded separately from GitHub Releases with its matching HACS integration. This page documents the *earlier Beta test* and does not make the isolated v3 QA component part of public HACS. [Stable installation instructions](INSTALL.md) remain authoritative for new users.

## Which components are which?

| Component | What it does | Installation / status |
| --- | --- | --- |
| **Kellie's Voice Android TV** | Voice/TALK, overlay, spoken answers, camera display and screensaver | Signed public v0.17.0 for normal use once released |
| **Kellie's Voice HACS integration** (`kellies_voice`) | TV device and controls; automation actions, alerts and camera events | Install separately from HACS, match the public APK |
| **Kellie's Voice Beta** (`com.example.tvvoiceprobe.qa`) | Separate **persistently signed** test TV app with Compose Settings and v3 pairing tests | v0.17 signed `app-qa.apk` from the manual nonpublishing QA workflow. Coexists with stable; does **not** inherit stable credentials. Disposable `app-debug.apk` is *not* an update for this app |
| **Kellie's Voice v3 QA** (`kellies_voice_v3qa`) | Isolated Home Assistant pairing/owner verification and renewable-login experiment | Draft QA-only ZIP, not the public HACS component; do **not** overwrite `custom_components/kellies_voice` |

**Home Assistant Assist is the conversation engine.** "Julia" / "Julie" are the maintainer's personal assistant name, not another product, a required model, or a wake phrase. Local LLMs are optional. `Okay Nabu` is the separately opted-in wake word; Google's remote microphone is not automatically shared with third-party applications.

## What is being tested in v0.17?

- **Compose Settings:** two-column six-category navigation, theme selection, voice/mic preferences, overlay and alert controls, screensaver, updates, and clearer focus states. The earlier public six-category *classic Settings* is distinct from this development Compose UI.
- **Quick Actions:** Home Assistant-editable TV buttons, device/action/name choices and icons, with remote-focus and persistence still part of release QA.
- **HA-first secure pairing v3:** discovery of the short-lived TV session, code comparison, physical TV remote OK, authenticated Home Assistant owner approval, optional dedicated TV login and encrypted handoff. This is **not** the existing public manual URL/token onboarding flow.
- **Home Assistant screensaver/dashboard:** preview, Frigate web or HA camera grid, live feeds when supported, and automatic Google TV ambient overlay via a separately running watcher.
- **Permissions:** independent microphone, wake word, camera/announcement receiver, Display over other apps, and Home Assistant layout opt-ins. Opening Settings never grants these permissions implicitly.

## Real onn 4K Pro hardware QA, October 31

| Test | Result | Important qualifier |
| --- | --- | --- |
| v3 8-digit comparison + physical remote | **Passed** | A matching code alone is not an HA login |
| Signed-in HA owner panel approval | **Passed** | Owner account does not transfer to the TV |
| Dedicated v3 login | **Connected and authenticated** | Owner explicitly opted in |
| Close and reopen beta | **Reconnected** | No manual credentials re-entered |
| Full Onn 4K Pro reboot | **Reconnected; Assist voice command worked** | Does not yet establish HA restart/revoke/token-refresh reliability |
| Screensaver Preview | **Rendered** | Preview bypasses idle trigger |
| Screensaver automatic idle with watcher RUNNING | **Passed** | Dashboard appeared on normal Google TV idle |
| Screensaver watcher start after reboot | **Passed on Onn combined Beta** | After a full TV reboot, Mobile Command appeared from normal Google TV idle, without opening Kellie's Voice or pressing Resync. Earlier build had failed; recovery is merged to the v0.17 integration branch via [PR #288](https://github.com/chadakenn/Kellies-Voice/pull/288) |
| HA dashboard login after reboot | **Passed on Onn** | Trusted-network login to Mobile Command persisted; no login page was displayed |
| HA dashboard chrome / remote Back | **Passed on Onn** | Combined Beta hides redundant Close/Select camera controls on HA pages; hardware Back dismisses dashboard after reboot |
| HA dashboard D-pad pointer scroll | **Passed on Onn earlier QA** | Verified in [PR #281](https://github.com/chadakenn/Kellies-Voice/pull/281); exact combined signed build should still receive a brief smoke test |
| Live doorbell via direct HA service | **Passed on Onn combined Beta** | `kellies_voice.show_live_camera` displayed visibly moving video, without invoking the LLM |
| Four-camera native grid/fullscreen audio | **Previously passed on older builds; not rerun on final combined Beta** | Must not infer four-feed playback and audio from one direct live doorbell popup |
| Home Assistant LLM camera phrase | **Unrelated agent error** | User's local agent reached a 20-tool-call limit. Direct HA camera delivery and live playback work; don't gate camera release on LLM tuning |

**Issue #126 hardware regression resolved on Onn:** both the standalone watcher-recovery QA and the combined signed QA performed unattended post-reboot activation. [PR #288](https://github.com/chadakenn/Kellies-Voice/pull/288) is now merged into `integration/v0.17.0-combined-qa` at `373d871`. These results are **device-specific**, not a promise for every Android TV firmware. The fallback, if Android denies a background start, is to open Kellie's Voice; on Android 13+ a recovery notification also requires the user's notification permission.

**Other release gates:** HA restart reconnect, refresh-token expiry/renewal, explicit revocation and replacement of a previously enrolled TV, two-TV isolation, native four-camera/fullscreen audio verification on the final combined build, D-pad and focus across themes and Quick Actions, independent security review [#214](https://github.com/chadakenn/Kellies-Voice/issues/214), and matching signed build/HACS QA. See [combined v0.17 QA PR #263](https://github.com/chadakenn/Kellies-Voice/pull/263). No public merge or release is implied by this document.

## Secure pairing: what testers must understand

The beta's opt-in ongoing login creates a **dedicated ordinary non-admin Home Assistant Users account**. Standard HA Users can access/control **all HA entities**, not just devices exposed to Assist. With a Home Assistant Cloud HTTPS origin, the account can also authenticate remotely. Use only for a personally trusted TV and only after explicit owner approval. Do not copy an HA owner's token to the TV. **The beta requires an HA HTTPS endpoint for renewable login**; local HTTP mDNS discovery/identity checks can still work without it.

If an already paired beta is **CONNECTED AND AUTHENTICATED** and a fresh pairing attempt reports `Existing TV enrollment must be resolved`, do **not** delete the journal or account blindly. The current combined QA guard refuses duplicate active/unresolved leases. Confirm existing service connectivity and follow the reviewed re-enrollment/revocation path when available.

## Screensaver troubleshooting in this QA build

1. Open **Beta → Settings → Screensaver → Screensaver status & recovery → Screensaver status & permissions**.
2. Check **Frigate feature ON**, source configured, **Overlay permission ALLOWED**, **Watcher RUNNING**, **Last Android dream event**, and **Last overlay outcome**.
3. On the tested Onn, reboot recovery is now automatic. If an *other* TV reports **Watcher NOT RUNNING**, record **Startup**, **Boot receiver this boot**, and **Last Android dream event** before using **Resync HA cameras** or opening the app, so the failure remains diagnosable. If Android blocks background startup, the app can resume from a visible Activity.
4. Return to Google TV, let it idle naturally, and check whether Kellie's Voice appears. Preview alone does not prove automatic operation.
5. Report the non-sensitive diagnostic fields and installed beta version. Do not share TV identity, private camera feed, HA URL, passwords or tokens.

## Real hardware screenshots, not design mockups

The [October 28 direct ADB screenshot gallery](screenshots/PR-192-CAPTURES.md) includes the six development Compose Settings categories, local theme choices, a neutral announcement preview, a masked voice overlay, and a privacy-redacted screensaver preview. These were captured from **0.16.14-test**, a developer APK **predating the newest v0.17 fixes**. They demonstrate the actual preview appearance, **not** a final v0.17 screenshot or a passing test of every feature.

The [original stable screenshot gallery](screenshots/README.md) contains verified voice overlay, live doorbell card, camera snapshot, and four-camera screenshots. Do not substitute old Settings images for new categories. Treat preview vs actual alerts and still vs live footage accurately. Keep private camera scenes, people, identifiers and addresses out of public media.

## Public documentation / publishing checklist

- [ ] Make README and HACS `info.md` explain why **Android APK + HACS integration are separate**, how to install matching versions, and the difference between manual stable pairing and QA automatic v3 login.
- [ ] Link current **real** ADB captures, with true version and feature labels, from README, HACS, screenshot index and Wiki. Don't present development theme screenshots as public release screenshots.
- [ ] Update Getting Started, TV Settings, HACS, Cameras/Frigate, Alerts, Troubleshooting, FAQ and YAML examples to match the **shipped stable** version; add clearly labeled v0.17 QA notes instead of replacing stable instructions too early.
- [ ] Check every in-repo wiki link and heading with `python3 scripts/check_wiki_docs.py`.
- [ ] After review and approved merge to `main`, separately run **Actions → Publish reviewed Kellie's Voice Wiki → PUBLISH**. The Wiki tab has its **own** Git repository; editing `docs/wiki` does not publish it.
- [ ] Before promoting v0.17 public, repeat hardware acceptance and replace beta wording/links with the **actual released** APK, version and HACS artifact. No imaginary screenshots or tests.
