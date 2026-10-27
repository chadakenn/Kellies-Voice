# TV Settings: categorized navigation (draft)

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

The standalone UI work lives in [draft PR #160](https://github.com/chadakenn/Kellies-Voice/pull/160), originally based on `dev`. This combined QA branch starts from the **current #59 wake draft** and adds a **Wake word** entry inside *Voice & microphone*, where the new audio-ducking percentage is available. It is an isolated test integration only, not a merge into `dev` or `main`.

## Manual TV QA

1. On the onn 4K Pro, open SETTINGS, navigate all six category cards with D-pad/OK, and check focus is visible without a mouse.
2. In each category, open each existing option. Change a harmless setting and confirm it returns to the same category.
3. Press remote Back from a category (returns to All settings), from a child menu (returns to its category), and from All settings (closes).
4. Check short screens and long category menus scroll to the focused option. Check all four themes and readability of focused/unfocused text.
5. Open diagnostics, preview, and Android system dialogs to confirm Back does not trap the user.
6. Ensure Home Assistant credentials, mic opt-out, updates and TV alert permissions remain intact. Test separately on the wake-word branch after integration.

**Status:** Draft awaiting CI + physical TV checks. No changes to stable `main`, `dev`, or the running APK.
