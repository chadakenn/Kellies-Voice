# TV Settings: categorized navigation

The settings menu is a dedicated **remote-first hub** rather than the former 21-item mixed list. The appearance uses the same `AppPalette` as Kellie's Voice, so existing Copper, Night Signal, Compact Charcoal, and Aurora theme choices still apply. The hub itself does not enable background services, the microphone, or any Home Assistant features.

## How to navigate

From the normal Kellie's Voice screen, press **SETTINGS**. Select a category with the D-pad and press **OK**. Each category contains only related options. Selecting an option opens its existing settings screen; saving a setting returns to the same category. The **Back** remote key or **Back** row returns from a category to **All settings**, and from a child setting to its category. **Close settings** returns to the voice screen.

| Category | Existing features |
| --- | --- |
| Home Assistant | Connect/reconnect; edit URL/token; start conversation; HA-managed layout |
| Voice & microphone | Sensitivity; hands-free follow-ups; safe mic input checks; diagnostics |
| Overlay & appearance | Open floating overlay; appearance, position, size, auto-hide, launcher/remote shortcut |
| Alerts & cameras | TV events; spoken alerts; announcement volume; test; receiver status; camera decoder |
| Screensaver | Frigate/HA camera dashboard configuration; preview |
| Updates & account | Release channel; install verified update; forget saved connection |

No settings are renamed or deleted. All existing option screens are reused. Dangerous microphone overrides, account deletion, and hardware decoder warnings keep their confirmation flows. The hub preserves selected theme colors and D-pad focus highlighting and uses content descriptions for the larger category targets.

## Branch integration plan

The categorized TV Settings and **Okay Nabu** controls are merged into `main` via [PR #165](https://github.com/chadakenn/Kellies-Voice/pull/165), for the v0.16.14 release. The **Wake word** and **Wake audio ducking** controls are under *Voice & microphone*. See [v0.16.14 release notes](releases/v0.16.14.md) and the [wake-word guide](WAKE_WORD.md).

## Manual TV QA

1. On the onn 4K Pro, open SETTINGS, navigate all six category cards with D-pad/OK, and check focus is visible without a mouse.
2. In each category, open each existing option. Change a harmless setting and confirm it returns to the same category.
3. Press remote Back from a category (returns to All settings), from a child menu (returns to its category), and from All settings (closes).
4. Check short screens and long category menus scroll to the focused option. Check all four themes and readability of focused/unfocused text.
5. Open diagnostics, preview, and Android system dialogs to confirm Back does not trap the user.
6. Ensure Home Assistant credentials, mic opt-out, updates and TV alert permissions remain intact. Test separately on the wake-word branch after integration.

**Status:** Published in signed stable v0.16.14. The previous v0.16.12 APK has the old menu. The maintainer tested the redesigned menu on an onn 4K Pro; broader visual and hardware acceptance remains ongoing.

**Media:** [Historical original Settings screenshot](screenshots/onn-settings-menu.jpg) is **not** representative of v0.16.14. New testers should capture a redacted photo of the six-category hub. [Real-device gallery and wake video](screenshots/README.md).

## Premium full-screen Settings (issue #162)

A dedicated full-screen `SettingsActivity` replaces the previous dialog-based hub for a more polished 10-foot UI.

- Uses the same `AppPalette` / theme preferences (copper/charcoal by default).
- Glanceable status chips for Connection, Wake word, and TV alerts at the top.
- Two-column layout: category cards on the left, options on the right.
- Strong focus indicators (thicker stroke + highlight color) for D-pad navigation.
- All existing settings and preference logic are preserved; actions are dispatched back to `VoiceActivity`.
- No new background services, no changes to voice handling or microphone recording paths.
- Readable at 720p / 1080p / 4K with density-independent padding and text sizes.

**Hardware note:** Physical onn 4K Pro testing is still required to confirm focus traversal, Back behavior, and readability under real TV conditions. Screenshots in the PR are mockups only; do not include private household information.

**Draft PR:** This redesign is delivered as a draft pull request and must not be merged into `main` until owner review and on-device QA.


## Navigation and Back behavior (PR #191 fixes)

VoiceActivity remains `singleTask` (required for remote shortcut / overlay). Selecting a setting from the full-screen SettingsActivity delivers an action to VoiceActivity. After the action or dialog is dismissed, VoiceActivity re-launches SettingsActivity (`FLAG_ACTIVITY_REORDER_TO_FRONT`) so the premium UI stays the host and the remote Back key returns to it.

Status chips are accurate:
- Credentials • Saved — credentials exist; does not claim a live WebSocket connection.
- Wake word • Listening / Stopped / Off — reflects the actual service state.
- TV alerts • Enabled / Off — reflects the preference; the scoped receiver connection is shown in the existing TV alert status dialog.

All six categories and existing safety confirmations are preserved. The Android compile error (`assist.newConversation()`) is fixed by calling the existing `VoiceActivity.newConversation()`.

**onn 4K Pro testing still required** for focus traversal, Back from nested dialogs, scroll on 720p, and readability on 1080p/4K.
