# Full-screen TV Quick Actions (development preview)

**Status: Draft feature, not installed/released.** These shortcuts belong ONLY to the full-screen Kellie's Voice main app, not the floating voice overlay. They work **without any microphone** by using the TV remote and the existing authenticated Home Assistant WebSocket.

## Configure per TV in Home Assistant

1. Install the matching **development test integration** on a disposable Home Assistant installation. Do NOT overwrite a working stable integration just to test this branch.
2. In **Settings → Devices & services → Kellie's Voice**, choose the desired **TV device → Configure**.
3. Under **TV settings and home-screen shortcuts**, fill up to four numbered slots:
   - **Label:** a short remote-visible name, e.g. `Movie Time` or `Kitchen On`
   - **Entity:** a valid `light.*`, `scene.*` or `script.*` entity ID, e.g. `scene.movie_time`
   - **Action:** `Turn on`, `Turn off`, `Toggle`. Scene and script entities support **Turn on only**.
4. Leave both **Label** and **Entity** empty to hide an unused slot. Save to reload the integration.
5. On the full-screen TV Home, the action row updates on reconnect, returning to the app, or a 30-second refresh. Select a tile and press **OK**. Home Assistant runs only the preconfigured action under the signed-in user's entity permissions.

Examples: **Kitchen On → light.kitchen → Turn on**, **Lights Off → light.living_room → Turn off**, **Movie Time → scene.movie_time → Turn on**, **Evening → script.evening → Turn on**. These are illustrations; the software does not preconfigure or trigger any user's actual lights.

## Security contract

- There is NO new web server or secret: requests run over the app's existing authenticated Home Assistant Assist WebSocket.
- TV receives just **slot number and label**, not the target entity or its configuration. The TV sends **only the slot number** on OK, never a service, entity ID, script body, template or token.
- The HA integration validates the paired TV, checks the logged-in HA user's read access to that TV, then verifies **control** permission for the configured target entity before sending a service call with that user's HA context.
- The server allows only explicit `light.turn_on/off/toggle`, `scene.turn_on` and `script.turn_on`; no arbitrary services or domains. Repeated presses within 800ms are rejected.
- Locks, covers/garage doors, alarms and security domains cannot be configured directly. HA scripts can perform sensitive actions; only trusted owners should configure scripts intended for TV shortcuts, and HA users must still have permission to control that script.
- This does not turn on the microphone, wake word, streaming audio, camera playback or TV popup. Existing TALK/STOP/SETTINGS remain available and shortcuts are not added to `VoiceOverlayService`.
- A new TV cannot invoke shortcuts until it is already configured and authenticated with Home Assistant. The #193 no-typing HA-first pairing feature is **not** completed here.

## Hardware acceptance checklist

- [ ] Signed `com.example.tvvoiceprobe.qa` install **over** the existing QA app (matching signing certificate and non-decreasing version code). Do not uninstall/clear app data or replace stable.
- [ ] Configure exactly one action and confirm only one tile appears, no extra buttons, no mic permission needed.
- [ ] Configure four actions; confirm readable labels in Copper, Neon and Jade on 720p/1080p and no clipping above TALK/STOP/SETTINGS.
- [ ] Using only D-pad LEFT/RIGHT/OK, select TALK, STOP, SETTINGS and every shortcut. Focus border must be visible and only one command fires per press.
- [ ] Power-cycle or disconnect HA; shortcut row clears or fails safely. Reconnect restores current definitions without app restart.
- [ ] Edit labels and actions in HA while the TV stays on the home screen; updates arrive within 30 seconds or on foreground return.
- [ ] Remove a slot in HA; its tile disappears and pressing its old slot cannot run an action.
- [ ] Try a non-admin HA user with read access to the paired TV but no control rights to a target; target is hidden and unauthorized presses denied.
- [ ] Attempt lock/garage/camera/invalid entity/long label/wrong service; all rejected by HA form and server policy.
- [ ] Existing Assist/TALK, cameras, announcements, update flow and overlay remain unchanged, including wake-word disabled.
- [ ] Test with two paired TVs; only each device's own configured slots are shown.

## Merge/release order

Draft shortcuts PR stacks on the [TV Home refresh #204](https://github.com/chadakenn/Kellies-Voice/pull/204), on [Jade theme #195](https://github.com/chadakenn/Kellies-Voice/pull/195), on [Compose #192](https://github.com/chadakenn/Kellies-Voice/pull/192). Keep all draft until the owner's physical onn 4K Pro tests and matching HA integration update. Add screenshots and final beginner docs only after the feature set stabilizes.

**Important:** This feature needs the **matching Android QA build and Home Assistant custom integration** installed together. Old HA/HACS releases do not implement `kellies_voice/get_home_shortcuts`, so no tiles will be available.
