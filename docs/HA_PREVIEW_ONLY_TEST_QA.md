## Known Home Assistant UI display issue and QA fallback

### User-visible regression reproduced on actual Home Assistant (Oct 29, 2026)

The owner installed the preview-only v0.16.14 custom component and
successfully completed the physical TV-OK proof, but the HA browser
rendered raw labels `start` / `numbers_match` and `preview_verified`,
and **did not render the eight-digit comparison code at all**. That is
a critical **human verification UX failure** even though ECDH proof
succeeded. Do not claim a complete visual code match based on this UI.

The updated preview-only QA build now adds a required, prefilled
`ha_code` field to the HA `pairing_code` form. Home Assistant's form
schema carries the exact server-generated eight-digit code even when
stale/missing custom integration translations suppress the description.
This field is meant only to DISPLAY the code; do not edit it. Backend
rejects an absent or altered value with `preview_code_missing` and
never checks TV confirmation in that case. The existing
`numbers_match` checkbox remains; do not check unless the TV shows
the **same** digits. Translations/en.json now also has a friendly label
for `ha_code`, The manifest remains at v0.16.14 to satisfy the repository release-policy
checks; the visible code field is independent of the translation cache.

**Test validation after replacing the QA folder and restarting HA:**
1. On the fresh HA preview flow, the step displays an input box
   containing an eight-digit Home Assistant code, even if the descriptive
   text remains untranslated.
2. TV independently displays the exact same number.
3. Only then press TV remote confirm, keep the prefilled code unchanged,
   check the box and submit.
4. The flow aborts with `preview_verified` or the friendly translation
   if localized; this is intentional safe preview completion.
5. If the field is absent or empty, do not check the box, cancel, and
   inspect HA's runtime translation installation and version.
6. Never register a config entry or install OAuth tokens from this flow.

Because the versioned integration contains existing user-facing controls,
only replace it after a full backup. Preserve/restore the original folder
if normal camera/alert/voice functions change. A hard browser refresh
after a backend restart can clear stale frontend text, but the form field
is deliberately NOT dependent on that.

# Safe HA pairing-code preview (draft #193, no OAuth)

This is a **separate, main-based Home Assistant test branch**, not the
unfinished OAuth branch. It adds ONLY the already hardware-verified
two-screen public-key comparison flow to the existing Kellie's Voice
Home Assistant custom integration.

## What it does

1. On the onn TV, launch the **separate** debug Pairing Lab installed from
   #227. Go to **Open LAN Two-Screen Code Preview** and press
   **Start Discovery Preview**.
2. For up to 120 seconds, the TV advertises a temporary
   `_kellies-pair._tcp.local.` mDNS service on the private LAN.
3. Home Assistant detects the temporary service and shows a
   **Kellie's Voice setup preview** discovered integration card.
4. Only after a logged-in HA user chooses **Start public-key comparison**
   does HA send an ephemeral, PUBLIC P-256 key to the TV, then
   calculate and display the eight-digit code.
5. The same eight digits must appear on the TV. Compare both screens
   and press **CONFIRM ON TV** with the physical remote.
6. Return to the HA form and confirm that you matched the digits and
   physically pressed OK. HA queries the TV's short-lived status and
   verifies its UUID-bound ECDH TV-OK MAC. The flow **ends** with a
   `preview_verified` success message.

That is deliberately the **final** step. It does **not** create a
config entry, request Home Assistant OAuth consent, retrieve or exchange
an access/refresh token, write to a secure store, change a TV URL or
microphone setting, or consider the TV automatically signed in.

The stable integration's existing manual TV pairing, voice, scripts,
alerts and cameras are unchanged by this branch's code, but replacing a
live component for testing always carries some operational risk.

## Getting a testable ZIP

Open the successful GitHub Actions **Safe HA pairing preview** check on
the draft PR and download the artifact named
`Kellies-Voice-HA-Pairing-Preview-Only`. It contains
`custom_components/kellies_voice/` plus only the three preview crypto
modules and existing production integration code.

**Do not install the combined unfinished OAuth draft** from PRs
#219–#226 on production Home Assistant. This intentionally narrower
branch is designed for the next hardware QA.

## Important live-install safeguards

- The test installation **replaces** the existing
  `/config/custom_components/kellies_voice/` code and requires an HA
  restart to be loaded. It is *not* as isolated as the TV debug APK.
- Create a **Home Assistant full backup first** and retain an exact copy
  of the current `kellies_voice` folder.
- Only install during an attended test when temporarily restarting Home
  Assistant won't disrupt household automations.
- Use Home Assistant's File Editor/Samba/Studio Code or another
  filesystem-accessible tool to copy the entire component folder with
  its original paths. Verify the manifest still declares
  `_kellies-voice._tcp.local.` alongside the new
  `_kellies-pair._tcp.local.` service. Don't copy individual Python
  files without the matching metadata/translations.
- Check HA config, restart, then observe the normal Kellie's Voice
  entities/alerts remain loaded.
- **Never** click Add to permanently register a test pairing preview:
  a successful preview is shown as an **abort with success text**, not
  an integration entry.
- Cancel and restore the previous component folder from the backup
  if any existing voice/camera/alert function behaves differently.
- This feature is only prepared for device QA. **It has not yet been
  installed or tested on the user's live HA server.**

## Diagnostic behavior

- No card: mDNS may not be reflected across LAN/VLANs, TV's 120-second
  window expired, or preview-only HA integration isn't loaded.
- Wrong digits: cancel on both sides, do NOT press TV confirm.
- `waiting_for_tv`: physical TV remote confirmation hasn't been
  observed. Press TV confirm only after matching digits, then submit
  the HA form again within the short window.
- `preview_expired`: restart discovery on the TV; start a fresh flow
  and compare the new digits.
- `preview_verified`: the UUID-bound physical TV-OK proof matched;
  no credentials or permanent configuration changed.

### Remaining release blockers

Live owner-bound OAuth/PKCE and exact credential handoff, trusted
transport, Assist + per-TV alerts authorization, credential Keystore
promotion, durable token revocation/crash recovery and #214 security
audit remain wholly separate. Do not merge this experimental test
branch into main or release it for general installation yet.
