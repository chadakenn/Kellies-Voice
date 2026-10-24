# Home Assistant HACS Setup

The Home Assistant side is a custom integration installed through HACS. It registers each paired TV and exposes actions such as `kellies_voice.announce`, `show_camera`, `show_live_camera`, `show_forecast`, `start_timer`, and more.

## Install the integration

1. Make sure [HACS](https://www.hacs.xyz/) is already installed in Home Assistant.
2. **First time only:** HACS → ⋮ → **Custom repositories** → Add  
   Paste `https://github.com/chadakenn/Kellies-Voice` and choose category **Integration**.
3. Open Kellie's Voice in HACS and download the **matching v0.16.9** integration.
4. **Restart Home Assistant**.
5. On the TV, turn **Settings → Home Assistant layout** ON and leave the app connected.
6. In Home Assistant go to **Settings → Devices & services**. Add the discovered Kellie's Voice device, **or** choose **Add Integration → Kellie's Voice** and enter the pairing ID shown on the TV under **Settings → Home Assistant layout → Show this TV's pairing ID**.

**Important:** The pairing ID shown on the TV is what you use for targeting actions. It is **not** the Home Assistant device-registry ID.

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

- [YAML Cookbook](YAML-Cookbook) for copy-and-paste examples of every action
- [TV App and Permissions](TV-App-and-Permissions)
- [Getting Started](Getting-Started)
