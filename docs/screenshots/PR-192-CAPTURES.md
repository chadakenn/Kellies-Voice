# Direct onn 4K Pro captures — PR #192

Captured October 28, 2026 through onn-tv-adb from the existing **0.16.14-test debug APK (version code 54)**. These are actual device captures with privacy redactions, not UI mockups. **Compose is a development-only preview, not released Settings.** The installed APK predates the latest PR #192 popup/theme fixes; these images do not verify those fixes. Its exact source commit was not established. The production app and installed APKs were not modified.

Private addresses, identifiers, camera names/footage, faces and background artwork are masked where present. Opaque redactions preserve source pixels elsewhere; the overlay uses additional background-pixel masking. The generative masking trial and unsolicited doorbell-alert frame are excluded. No raw files are included. Theme was restored to Aurora Glass. Auto-listen was restored to its original ON value after the owner-authorized idle capture.

## Home Assistant category

![Home Assistant category](compose-home-preview.png)

Compose development preview. Local host/address and pairing identifier are covered by opaque masks.

## Voice & microphone category

![Voice & microphone category](compose-voice-preview.png)

Compose development preview. LISTENING is this TV’s existing opted-in wake-word state, not the default. No wake setting was changed.

## Overlay & appearance — Aurora Glass

![Overlay & appearance — Aurora Glass](compose-aurora-preview.png)

Compose development preview with Aurora Glass selected. This is the original theme restored after capture.

## Alerts & cameras category

![Alerts & cameras category](compose-alerts-preview.png)

Compose development preview showing the existing alert and spoken-announcement switches and Top left selection. No spoken test was invoked.

## Screensaver category

![Screensaver category](compose-screensaver-preview.png)

Compose development preview. Private camera entity names masked; Frigate web URL unconfigured.

## Updates & account category

![Updates & account category](compose-updates-preview.png)

Compose development preview showing installed 0.16.14-test and Stable public releases selected. No update performed.

## Copper Glass selected

![Copper Glass selected](compose-copper-preview.png)

Actual local theme selection in the installed Compose development preview.

## Compact Charcoal selected

![Compact Charcoal selected](compose-charcoal-preview.png)

Actual local theme selection in the installed Compose development preview.

## Night Signal selected

![Night Signal selected](compose-night-preview.png)

Actual local theme selection in the installed Compose development preview.

## Neon Obsidian selected

![Neon Obsidian selected](compose-neon-preview.png)

Actual local theme selection in the installed Compose development preview. The installed HA theme catalog lacked Neon; this is not evidence of HA synchronization or released support.

## Local announcement sample

![Local announcement sample](announcement-preview.png)

Built-in Preview announcement popup over Compose development settings, using neutral sample text. Not a new HA delivery or spoken-audio test.

## Idle voice overlay — heavily redacted

![Idle voice overlay — heavily redacted](voice-overlay-idle-redacted.png)

Original foreground text, waveform and opaque controls retained at captured positions. Launcher artwork, surrounding content and translucent background pixels masked. Does not show complete card styling. Auto-listen temporarily OFF with owner permission, then restored ON; TALK not pressed.

## Screensaver preview — camera area fully masked

![Screensaver preview — camera area fully masked](frigate-overview-redacted.png)

Entire camera-content region including footage, labels and timestamps covered. Only the genuine Close control is retained. Not evidence of moving playback, camera layout or decoder reliability. Preview closed with Back.
