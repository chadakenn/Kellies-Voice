# TV home screen visual refresh: QA checklist

**Draft visual-only feature.** It builds on the latest Jade Terminal/Compose Settings branch, **not production main**. The stable public version, saved Home Assistant login, mic ownership, wake-word policy and separate QA updater are not modified.

## What changed

- The old plain-text title, long HA URL and three default Android buttons become a readable, theme-aware full-screen home.
- Large **Kellie's Voice** masthead, privacy-respecting **Home Assistant voice / TV alerts** connection summary (no raw HA URL), compact actionable status strip, separate scrollable **Kellie's Response** and **You Said** conversation cards.
- Bigger **TALK / STOP / SETTINGS** remote actions with a clearly visible focus border; no new controls, buttons, permissions, listening behavior, background services or network endpoints.
- The same `AppPalette.forSkin(...)` used by Settings and overlay now colors the home header, status cards, text, focus rings and buttons when a theme changes. Existing text content and Assist client are **not recreated** on theme change.
- Debug/signed QA SETTINGS still opens Compose. Production SETTINGS still opens the existing safe Java hub. The configured onn star-button overlay shortcut is unchanged.

## Required real onn 4K Pro QA (not performed yet)

1. Using the **persistently signed** `com.example.tvvoiceprobe.qa` APK (not an ephemeral PR debug build), install **over** existing QA if Android accepts the signature and version. Do not uninstall, clear data or replace stable.
2. On 720p/1080p/4K: verify masthead, HA voice and TV alert states, status, complete voice answer, transcript and all three actions fit and remain readable. Long answers must remain scrollable; neither answer nor status can hide TALK.
3. Use the D-pad: first focus is TALK, Right/Left correctly chooses TALK/STOP/SETTINGS; OK activates only the focused action; remote Back and star-button launcher fallback still work. Check contrast when focused and unfocused.
4. Switch **Copper Glass → Neon Obsidian → Jade Terminal → Copper Glass** in Settings; return to Home. Verify every card and button recolors immediately, including bottom row, header, and connection summary. The existing conversation must be preserved.
5. Existing HA credentials: connected/disconnected/not configured states must display correctly, including when HA restarts. The screen must not display the saved HA URL, token or pairing ID.
6. Voice: TALK starts the same original Assist/mic path after permission, response and recognition text update, STOP ends the request, TV speakers and audio ducking behave as before. No unsolicited mic/wake or camera decoder activation.
7. From SETTINGS, verify Compose focus, keyboard Back after editing Frigate URL/cameras, six skin choices, HA style sync under matching test integration, and recovery actions. Do not treat this home PR as completing those separate theme gates.
8. TV reboot, app restart, screensaver, full-screen doorbell camera and overlay launch must behave as before. Redact photos/video before GitHub uploads.

## Shipping

- Keep this PR **draft** and based on Jade theme #195 (which itself depends on Compose #192).
- Merge theme/settings only after owner hardware signoff; then rebase the home refresh onto the approved branch. No immediate main release, no change to HACS/Marketplace PR #199, and no auto-login claim for unfinished #193.
- Source regression tests: `tests/test_tv_home_refresh.py`; Android JNI/unit and HA/HACS checks must be green on the exact installed SHA.

See [Compose Settings QA](COMPOSE_TV_QA.md) and [theme PR #195](https://github.com/chadakenn/Kellies-Voice/pull/195).
