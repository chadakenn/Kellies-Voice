# Cameras and Frigate

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

Kellie's Voice can show camera snapshots and experimental muted live HLS overlays over whatever you are already watching.

## What it looks like on the TV

These photos are from the owner's onn 4K Pro in October 2026. The four-camera grid and the Frigate website are on the **development** build, not stable v0.16.11. A person is visible in the Frigate event strip.

![Four muted live cameras and a Close button](https://raw.githubusercontent.com/wiki/chadakenn/Kellies-Voice/onn-live-camera-grid.jpg)

![Frigate website with the Kellie's Voice Close button](https://raw.githubusercontent.com/wiki/chadakenn/Kellies-Voice/onn-frigate-dashboard.jpg)

![Frigate screensaver diagnostics. The watcher is not running in this photo](https://raw.githubusercontent.com/wiki/chadakenn/Kellies-Voice/onn-frigate-screensaver-diagnostics.jpg)

The diagnostics photo is an example of the status screen, not a working screensaver. Here the watcher is stopped, Home Assistant layout sync has not started, and Android has not sent a dream event.

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
