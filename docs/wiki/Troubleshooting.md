# Troubleshooting

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

## Resume TV alerts (exact steps)

Camera, announcement, timer, and weather popups use a separate receiver from the main voice connection. Even if **TALK** works and the main screen says Connected, the alert receiver can drop after standby, a TV power cycle, an app update, or a Home Assistant reload.

**Exact on-TV path (v0.16.6 and newer):**

1. Open **Kellie's Voice** on the TV (not Android/Google TV system Settings).
2. On the main screen choose **SETTINGS** with the remote.
3. **Scroll down** to **TV alert status** and press **OK**.  
   (This is a separate SETTINGS item, below the **TV announcements & cameras** toggle.)
4. In the **TV alert status** dialog, select the **Resume TV alerts** button at the bottom and press **OK**.

It is **not** the neighboring **TV announcements & cameras** toggle and **not** the main **Connect / reconnect** control.

**If the Resume button is missing:**

- Turn **Settings → TV announcements & cameras** ON.
- Confirm a valid Home Assistant connection is configured.
- Allow Android **Display over other apps** permission for Kellie's Voice.

**In Home Assistant:** the paired TV's **TV alert receiver connected** diagnostic should become ON after a successful resume. This means the TV is subscribed — it does **not** guarantee that any particular popup already rendered. Send a **fresh** test announcement, camera, or timer. Events that occurred while the receiver was disconnected are **not** replayed.

Manual Resume has been confirmed on an onn 4K Pro. With v0.16.9, a Home Assistant restart also reproduced a misleading permission-denied status while the receiver was actually disconnected. Pressing Resume restored it without changing account permissions. The original [issue #78](https://github.com/chadakenn/Kellies-Voice/issues/78) is closed, but follow-up automatic recovery work is being tested on the `dev` branch. This is **not** proof that reconnection works after standby on every device.

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
