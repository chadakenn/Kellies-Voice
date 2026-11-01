# Cameras and Frigate

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

Kellie's Voice can show camera snapshots, opt-in HLS camera overlays, and an optional automatic screensaver with up to four muted live cameras.

## What it looks like on the TV

These photos are from the owner's onn 4K Pro in October 2026. The four-camera grid and the Frigate website are included in the **stable v0.17.0** release (introduced in v0.16.12), opt-in and subject to camera/decoder compatibility. The home camera feeds and event strip are deliberately obscured for privacy.

![Four muted live cameras and a Close button](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/docs/screenshots/onn-live-camera-grid.jpg)

![Frigate website with the Kellie's Voice Close button](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/docs/screenshots/onn-frigate-dashboard.jpg)

![Frigate screensaver diagnostics. The watcher is not running in this photo](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/docs/screenshots/onn-frigate-screensaver-diagnostics.jpg)

The diagnostics photo is an example of the status screen, not a working screensaver. Here the watcher is stopped, Home Assistant layout sync has not started, and Android has not sent a dream event.

## Additional direct ADB captures from PR #192 testing

![Compose development preview for Frigate screensaver settings with camera entity names masked](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/feature/compose-tv-settings/docs/screenshots/compose-screensaver-preview.png)

Screensaver settings on the existing **0.16.14-test debug app**, captured October 28, 2026 on an onn 4K Pro. **This Compose settings page is a development preview**, not the released settings interface. Camera entity names are masked; the Frigate web URL was unconfigured.

![Actual screensaver preview with the full camera area masked and the Close control retained](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/feature/compose-tv-settings/docs/screenshots/frigate-overview-redacted.png)

The screensaver preview opened from that development UI. All camera imagery, tile labels, timestamps, faces and scene details are covered by one opaque mask; only the genuine **Close** control remains visible. This deliberately does not show the camera layout, prove moving video, or establish decoder reliability. Preview was closed with Back. No camera mode was changed. [Capture details](https://github.com/chadakenn/Kellies-Voice/blob/feature/compose-tv-settings/docs/screenshots/PR-192-CAPTURES.md).

## Live HA camera screensaver (opt-in; introduced in v0.16.12)

Enable **Frigate screensaver** and **Home Assistant layout** in the TV's Kellie's Voice settings and grant **Display over other apps**. Select up to four Home Assistant camera entities for the paired TV in Home Assistant. Leave the **Frigate web URL blank** to use the HA-selected grid; a Frigate URL takes priority and shows the Frigate website instead.

In the paired TV's Home Assistant controls choose **HA camera grid playback → Live (one camera)** first, then two or four (advanced) if the TV handles software-decoded HLS reliably. The overview is **muted**, and failed/unsupported HLS returns to snapshots. Use the remote D-pad to select a tile, press **OK** for full-screen and that camera's audio if the stream contains an audio track, and press **Back** to restore the muted 2×2 grid. **Close** ends the viewer. The focused footer control has a gold outline.

On the maintainer's onn Google TV, all four live cameras launched automatically and returned after reboot. Full-screen Doorbell audio, remote Back and focus were field-tested. This does **not** establish other devices, all decoders or audio tracks. See [Frigate screensaver setup](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/FRIGATE_SCREENSAVER.md) for the diagnostics and Resync HA cameras recovery.

## CleanMyTV Home launcher compatibility (onn 4K Pro, field-tested November 1, 2026)

[CleanMyTV](https://github.com/wingedpanther/cleanmytv) can be used as the ad-free Android/Google TV home launcher alongside Kellie's Voice's automatic camera screensaver. The maintainer verified the combination on an **onn 4K Pro**, including automatic idle entry and the four-camera screensaver.

**Important:** CleanMyTV Home has a separate **Idle screen** (clock and wallpaper). That is an in-launcher idle view, **not** Android/Google TV Ambient Mode, and it does not itself trigger Kellie's Voice's screensaver watcher.

1. On your computer open **CleanMyTV → Home screen → Layout → Idle screen** and set it to **Off**.
2. Keep Kellie's Voice's **Frigate screensaver** enabled, with its Home Assistant camera source configured and Android **Display over other apps** permission granted.
3. Return to the CleanMyTV Home screen, leave the remote untouched, and allow the TV's **normal Android/Google TV Ambient Mode** idle timeout to elapse. Do not expect the camera grid to appear immediately when CleanMyTV's own idle clock is disabled.
4. When Android Ambient Mode starts, Kellie's Voice should show its camera view if the screensaver watcher is running. On the maintainer's onn 4K Pro, this **worked automatically** after disabling CleanMyTV's Idle screen.

**If the cameras appear only when you manually start Google Ambient Mode:** the camera viewer and dream trigger work; check whether the system is actually entering Ambient Mode after its idle timeout. If no overlay appears when Ambient Mode starts, inspect **Kellie's Voice → Settings → Screensaver → Screensaver status & permissions** for watcher status, overlay permission, last Android dream event, and overlay result. A working **Preview** alone does not prove automatic idle activation. See [screensaver diagnostics](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/FRIGATE_SCREENSAVER.md).

This is a **single-device compatibility confirmation**, not a guarantee for every CleanMyTV-supported TV or firmware. No CleanMyTV uninstall, launcher rollback, or Kellie's Voice code change was needed for the verified setup.

## Philips OLED factory screensaver covering the dashboard (community report, November 2026)

**Reported on:** Philips **55OLED856/12** (OLED, firmware `TPM211EA_R.101.001.005.115`), Kellie's Voice **0.17.1**, by [@voravin in issue #261](https://github.com/chadakenn/Kellies-Voice/issues/261#issuecomment-6562897320). Their separate Philips **65PUS8507/12** LED TV (app 0.17.3) also passed Home Assistant dashboard login/navigation and camera-popup checks, but **the factory-logo interference was reported only on the OLED model**. This is a community test, not a universal Philips fix.

### Symptoms and cause

After approximately **two minutes** of inactivity or an unchanged picture, Philips firmware can display its own **bouncing-logo screensaver** *above* Kellie's Voice's Android screensaver/dashboard. In the report, `dumpsys dreams` showed `FrigateScreensaverDream` still running and `dumpsys window` showed a visible `org.droidtv.tvsystemui` window above it. So a visible Philips logo **does not necessarily mean** that Kellie's Voice stopped or lost its Home Assistant login. The tester could not disable this vendor behavior through the Philips TV menu.

### Advanced workaround reported by the tester

The tester found that starting Android's screensaver **before** the Philips logo normally appears, while regularly changing the dashboard layout, kept their dashboard visible. This is **optional and model/firmware-specific**, not a recommended default setting or a verified permanent solution.

1. First review the TV's normal **screen protection, sleep and screensaver** settings. Prefer the manufacturer's intended protections, particularly for OLED.
2. **Advanced, ADB only:** with Android debugging already configured, the tester used:

   ```sh
   adb shell settings put system screen_off_timeout 60000
   ```

   This requests a **60,000 ms (one-minute)** Android inactivity timeout; it does not disable the Philips firmware's own screensaver. The Philips menu exposed a five-minute minimum, and firmware sometimes reset the ADB value after a reboot or update. Read it back with `adb shell settings get system screen_off_timeout` if behavior changes. Do **not** assume this command works on other Philips models.
3. The tester **rotated the dashboard layout at least every 30 seconds**. That's a report of their setup, **not a guarantee against OLED image retention or burn-in**. Maintain dimming, sleep and manufacturer panel-protection features; avoid leaving bright static widgets or camera frames on an OLED for long periods.
4. If the Philips logo still appears, do not keep defeating panel-protection behavior just to force Kellie's Voice to remain visible. Use the manufacturer's screensaver or normal display power-off behavior instead.

### One unconfirmed TV freeze

The tester reported **one non-reproducible full-TV freeze** after dismissing the Philips logo and pressing the remote **Back** button: the display went blank and neither the remote, ADB nor the Philips API responded until power cycling. The cause is **unknown**; there is no evidence establishing that Kellie's Voice caused the freeze. They subsequently used **OK** to dismiss the Philips logo before using Back. If it happens again, report firmware, app version, exact key sequence and redacted diagnostics in [issue #261](https://github.com/chadakenn/Kellies-Voice/issues/261).

**Verified limits:** On both Philips TVs the reporter confirmed Home Assistant trusted-network login without the old `Please wait` stall, remote D-pad navigation/Back, dashboards over TiviMate and HDMI, and camera popups. **Automatic screensaver start after a full TV reboot was not yet tested** in that report.

## Snapshot popups

Use `kellies_voice.show_camera`. This is a still image. Optional parameters (v0.16.5+):

- `show_header: false` — hide title and caption
- `image_fit: fit` — show the entire frame (letterboxed) instead of cropping
- `refresh_seconds: 3` — periodically update the still (2–30 seconds)

See full examples in the [YAML Cookbook](YAML-Cookbook.md#snapshot-camera).

## Live camera (experimental)

Use `kellies_voice.show_live_camera`. Moving video has been verified on the onn 4K Pro with driveway and doorbell cameras. Other devices and cameras need testing.

On Philips integrated TVs, prefer the **Safe software** live-camera mode in TV Settings so the hardware decoder stays available for HDMI/Netflix. Hardware mode is opt-in and can disrupt HDMI.

Short durations (down to 4 seconds) and the same header/fit/refresh options are supported in v0.16.7+. A very short popup may close before the first HLS frame arrives.

## Hide a camera

`kellies_voice.hide_camera` dismisses the current camera popup immediately.

## Frigate person detection

Use the included blueprint `frigate_person_popup.yaml` or a simple automation that calls `show_camera` when a Frigate person occupancy sensor turns on. This produces a **snapshot**, not live video.

Full YAML is in the [YAML Cookbook](YAML-Cookbook.md#frigate-person-automation--blueprint).

## Voice control of cameras

Create clearly named scripts (e.g. "Show doorbell camera live on TV") and expose them to Assist. Parameterized durations are less reliable with some conversation agents; fixed-duration scripts are more dependable. See the repository's [docs/VOICE_CAMERA_SCRIPTS.md](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/VOICE_CAMERA_SCRIPTS.md) for complete script examples.

## Next

- [YAML Cookbook](YAML-Cookbook.md)
- [Troubleshooting](Troubleshooting.md)

---

[← All documentation](Home.md) · [Next: YAML Cookbook →](YAML-Cookbook.md)
