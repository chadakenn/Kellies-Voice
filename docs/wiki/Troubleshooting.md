# Troubleshooting

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

## Resume TV alerts (exact steps)

Camera, announcement, timer, and weather popups use a separate receiver from the main voice connection. Even if **TALK** works and the main screen says Connected, the alert receiver can drop after standby, a TV power cycle, an app update, or a Home Assistant reload.

**Exact on-TV path (v0.16.6 and newer):**

1. Open **Kellie's Voice** on the TV (not Android/Google TV system Settings).
2. On the main screen choose **SETTINGS** with the remote.
3. Open **Alerts & cameras → TV alert status** and press **OK**.
4. In the **TV alert status** dialog, select the **Resume TV alerts** button at the bottom and press **OK**.

It is **not** the neighboring **TV announcements & cameras** toggle and **not** the main **Connect / reconnect** control.

**If the Resume button is missing:**

- Turn **Settings → Alerts & cameras → TV notifications & camera popups** ON.
- Confirm a valid Home Assistant connection is configured.
- Allow Android **Display over other apps** permission for Kellie's Voice.

**In Home Assistant:** the paired TV's **TV alert receiver connected** diagnostic should become ON after a successful resume. This means the TV is subscribed — it does **not** guarantee that any particular popup already rendered. Send a **fresh** test announcement, camera, or timer. Events that occurred while the receiver was disconnected are **not** replayed.

Manual Resume has been confirmed on an onn 4K Pro. With v0.16.9, a Home Assistant restart also reproduced a misleading permission-denied status while the receiver was actually disconnected. Pressing Resume restored it without changing account permissions. The original [issue #78](https://github.com/chadakenn/Kellies-Voice/issues/78) is closed, but follow-up automatic recovery work is being tested on the `dev` branch. This is **not** proof that reconnection works after standby on every device.

## v0.17 Beta: preview works, but screensaver does not open on idle

In the developer-only v0.17 combined beta, open **Settings → Screensaver → Screensaver status & recovery → Screensaver status & permissions**. If the screen says **feature ON**, **source configured**, **overlay ALLOWED**, but **Watcher NOT RUNNING**, the preview alone does not establish automatic screensaver support. On an onn 4K Pro after reboot, **Resync HA cameras** started the watcher and the Home Assistant dashboard then appeared automatically when Google TV went idle. This is a **temporary beta workaround**, not a finished reboot-recovery fix; see [issue #126](https://github.com/chadakenn/Kellies-Voice/issues/126). Don't change secure pairing or HA login to troubleshoot a stopped watcher. Normal v0.16.14 release instructions remain above and below.

## Connected but no speech is recognized

A TV can show **Home Assistant voice: ✓ connected** and **TV alerts: ✓ connected** while **YOU SAID: Nothing yet** stays unchanged. These labels confirm connections, not that Android has granted access to a usable microphone.

1. Press **TALK**, say a short phrase, and check for a transcript under **YOU SAID**.
2. Confirm Android has granted Kellie's Voice the **Microphone** permission.
3. Open **SETTINGS → Voice & microphone → Test microphone hardware**. Check that an actual microphone is available, not only an echo-reference or audio playback input.
4. Keep **Microphone input checks: Safe** for normal use. **Try unverified microphone (compatibility override)** is for controlled tests of physically present, misclassified inputs; it cannot create a microphone when Android exposes none.
5. If the device has no usable microphone, try a compatible USB mic. Google Assistant's remote microphone may be reserved for Google's own app.
6. Report the *actual Android/Google TV device model*, Android version, APK/HACS versions and non-sensitive diagnostics in [issue #71](https://github.com/chadakenn/Kellies-Voice/issues/71).

**Do not mistake remote focus for a setting:** a bright blue row is only the highlighted choice. **Disable microphone capture on this TV** deliberately blocks recording when activated. For the full menu see [Microphone routing](TV-App-and-Permissions.md#microphone-routing-and-phantom-inputs).

## Other common issues

- **No microphone detected / silent STT** — some Philips TVs expose only echo-reference or line-in devices that are not usable for speech. As of v0.16.8, those TVs can still incorrectly show Listening; see [issue #71](https://github.com/chadakenn/Kellies-Voice/issues/71). Try a compatible USB microphone and share non-sensitive Hardware microphone diagnostics with the maintainers. Use the unverified microphone override only for troubleshooting genuine, misreported microphones. The Google Assistant remote button is not intercepted.
- **Action succeeds in Home Assistant but nothing appears on the TV** — check the alert receiver status, pairing ID (must be the TV's pairing ID, not an HA device-registry ID), and that the matching APK and HACS versions are installed.
- **"This device doesn't support timers"** — Home Assistant's native timer intent was used. Direct `kellies_voice.start_timer` actions work; voice routing requires extra configuration. See [Voice and Timers](Voice-and-Timers.md).
- **Signature conflict on update** — do not uninstall a signed release just to update. Use the in-app updater or install the newer signed APK over the existing one.

Full repository troubleshooting: [docs/TROUBLESHOOTING.md](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/TROUBLESHOOTING.md).

## Next

- [Getting Started](Getting-Started.md)
- [YAML Cookbook](YAML-Cookbook.md)

---

[← All documentation](Home.md) · [Next: FAQ →](FAQ.md)
