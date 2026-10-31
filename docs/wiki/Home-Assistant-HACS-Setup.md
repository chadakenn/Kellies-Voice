# Home Assistant HACS Setup

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

The Home Assistant side is a custom integration installed through HACS. It registers each paired TV and exposes actions such as `kellies_voice.announce`, `show_camera`, `show_live_camera`, `show_forecast`, `start_timer`, and more.

## Install the integration

1. Make sure [HACS](https://www.hacs.xyz/) is already installed in Home Assistant.
2. **First time only:** HACS → ⋮ → **Custom repositories** → Add  
   Paste `https://github.com/chadakenn/Kellies-Voice` and choose category **Integration**.
3. Open Kellie's Voice in HACS and download the **matching v0.17.0** integration.
4. **Restart Home Assistant**.
5. On the TV, turn **SETTINGS → Home Assistant → Manage TV layout from Home Assistant** ON and leave the app connected.
6. In Home Assistant go to **Settings → Devices & services**. Add the discovered Kellie's Voice device, **or** choose **Add Integration → Kellie's Voice** and enter the pairing ID shown on the TV under **SETTINGS → Home Assistant → Manage TV layout from Home Assistant → Show this TV's pairing ID**.

**Important:** The pairing ID shown on the TV is what you use for targeting actions. It is **not** the Home Assistant device-registry ID.
**Extra protection for manually entered IDs (next development version):** the confirmation page now asks you to enter the **last four characters from the pairing ID displayed on the TV**. A mismatched suffix cannot create the HA entry. This catches a mismatch **only in the final four characters**; compare the entire ID carefully. It does not prove the TV is connected or that an announcement was delivered. The paired TV's receiver diagnostic must be checked separately after setup; old or disconnected TVs may remain offline.


## v0.17 developer QA is a separate flow

**For normal public v0.17.0 users, use the HACS steps above.** The separate owner-approved v3 QA environment introduced an opt-in Home Assistant-first `Secure pairing v3` test: verify eight digits, press OK on the physical TV remote, and independently approve the same TV from the HA owner panel. Its optional dedicated TV account can control **all HA entities** as a standard non-admin user and needs HTTPS for renewable login. The isolated `kellies_voice_v3qa` component is **not** the regular HACS integration and must never replace `custom_components/kellies_voice`.

For current real-device results, installation boundaries and unresolved security/recovery items, see the [v0.17 QA guide](https://github.com/chadakenn/Kellies-Voice/blob/docs/v017-complete-onboarding-media-qa/docs/V017_BETA_STATUS.md). Do not present it as a released zero-touch pairing flow.

## After pairing

Open the TV device page in Home Assistant. You will see controls for:

- Voice overlay position, size, style (including Aurora Glass), auto-hide, auto-listen
- Microphone sensitivity (Quiet room / Normal / TV on)
- TV alert position and size (separate from the voice overlay)
- Forecast source, position, size, and duration
- Diagnostic: **TV alert receiver connected**

Changes usually take effect on the next connection or the next popup.

## Manual installation (alternative)

Copy the entire `custom_components/kellies_voice` folder into your Home Assistant `config/custom_components/` directory, then restart Home Assistant and add the integration as above. HACS is preferred because it handles updates.

## Next

- [YAML Cookbook](YAML-Cookbook.md) for copy-and-paste examples of every action
- [TV App and Permissions](TV-App-and-Permissions.md)
- [Getting Started](Getting-Started.md)

---

[← All documentation](Home.md) · [Next: YAML Cookbook →](YAML-Cookbook.md)
