# Kellie's Voice: Full App Beta Test Kit

**Development-only beta.** This builds the real Kellie's Voice Android TV app,
with the full Assist/camera/announcement/overlay/screensaver feature set and an
additional **Full app beta test center** in
**Settings → Home Assistant**, next to the debug-only v3 secure owner test.

## Read this before installing

- The beta APK uses a **separate Android application ID**
  `com.example.tvvoiceprobe.debug` and appears as **Kellie's Voice Beta**.
  Your existing signed stable app `com.example.tvvoiceprobe` remains installed
  with its settings/login intact. Do **not** uninstall or clear data for the
  stable app.
- Beta storage is **separate**. You must configure the beta's regular HA
  address and access token independently to exercise Assist, alerts and camera
  functions. Copy credentials only through a private, trusted method.
  **Never share a token in screenshots, GitHub, or chat.** A local HTTP token
  connection is not encrypted; use HTTPS when possible.
- The v3 pairing test exchanges ephemeral P-256 keys and records real signed-in
  HA-owner approval with a one-use authenticated receipt. **It does not
  provision a TV login**, create an HA user, activate Assist/camera permissions,
  or save credentials. A successful v3 pairing does not change the full-app
  connection. This limitation is explicit in the TV beta center.
- Only use the draft-PR beta artifact with the matching isolated QA Home
  Assistant integration. **Do not install this package into public HACS or
  merge to main.**

## One ZIP containing both test components

From this PR's passing **Unit tests** workflow, download the Actions artifact
`Kellies-Voice-full-app-beta-kit`. The outer GitHub artifact ZIP contains
`Kellies-Voice-Full-App-Beta-Kit.zip`. Extract the inner kit, containing:

- `app-debug.apk` (full Android TV beta application)
- `kellies_voice_v3qa_ha.zip` (the **isolated** HA v3 QA component)
- `FULL_APP_BETA_TEST_GUIDE.md` (this checklist)
- `SHA256SUMS.txt` (file hashes)

The HA integration ZIP is **nested**. Extract it into the HA
`/config/` folder with directory structure preserved, so this exact
file exists:
`/config/custom_components/kellies_voice_v3qa/frontend/kellies-voice-v3qa-owner-panel.js`.

Never overwrite `/config/custom_components/kellies_voice/`, which is the
stable HACS integration. Ensure the existing single top-level
`kellies_voice_v3qa:` entry is present in `configuration.yaml`; run Check
Configuration then restart HA. Check the owner panel:
`/kellies-voice-v3qa-approval`. If it is missing, stop and inspect HA logs.

## Full app smoke test

1. Sideload the **debug** APK as a *separate app*, launch
   **Kellie's Voice Beta**, and open
   **Settings → Home Assistant → Full app beta test center (DEBUG)**.
2. Review the live readiness report. For the **actual full app** tests,
   configure the beta's regular HA URL and a token under
   **Edit Home Assistant address & token**. Verify
   **HOME ASSISTANT: CONNECTED AND AUTHENTICATED**. A successful v3 owner
   approval is not a substitute for this login.
3. Press TALK, ask one ordinary Assist question, confirm recognition, voice
   reply, and correct TV output. Repeat to test conversation continuity.
4. Open the floating voice overlay. Confirm close/Back and that the mic stops
   when the overlay is dismissed. Check manually disabled mic remains blocked.
5. Enable **TV notifications & camera popups** (user opt-in). Grant Android's
   **Display over other apps** permission. Open **TV alert status & connection**,
   choose Resume if needed. Confirm it says connected.
6. From HA send a **new** test announcement and a voice-open camera request,
   confirm a moving image where supported, close it, and verify TV remote
   controls. A connected receiver does not prove a popup was delivered.
7. With spoken announcements opted in, run the local spoken alert test.
   Verify media volume restores after speech.
8. Preview the Frigate screensaver, test camera selection/fullscreen/back and
   confirm it does not auto-enable any unrequested microphone.
9. Restart the beta and temporarily interrupt Wi-Fi. Check Assist and TV
   alerts reconnect without enabling the mic. Stable app remains untouched.
10. Run **Secure pairing v3 (DEBUG TEST ONLY, no login)**, keeping the TV
    foreground. In HA complete the eight-digit physical remote confirmation
    and authenticated owner's second-tab approval inside **the original two
    minutes**. The required results are
    `owner_receipt_verified_no_login` in HA and
    **SECURE OWNER APPROVAL VERIFIED** on TV. No login is created.

## Expected safe failures

- Wrong code, non-owner approval, rejected owner, expired window, wrong TV,
  tampered ciphertext, missing encrypted acknowledgement, TV leaving the
  foreground, or HA restart must **not** result in v3 success.
- The full-app beta should not silently enable wake-word, mic recording,
  camera popups, overlay permission, or arbitrary HA services.
- If v3 returns `owner_receipt_failed`, preserve the error and the relevant
  HA logs, **without posting tokens or key material**.

## Rollback

Uninstall only **Kellie's Voice Beta** (`.debug` package). To remove HA
test integration, remove the single `kellies_voice_v3qa:` YAML entry and
delete only `/config/custom_components/kellies_voice_v3qa/`, then check
the configuration and restart HA. Keep the public HACS `kellies_voice`
folder and the stable Kellie's Voice app intact.

## Release blockers beyond this beta

- No-typing, permanent login via a dedicated, least-privilege HA identity
- Secure enrollment that survives TV/HA restart, with revocation and recovery
- Whole-Assist and alert authorization enforcement, not only a QA permission
  checker
- Independent security review (#214), 2-TV isolation, malicious LAN peer QA
- Production signing, migration and actual release verification

This kit is a **full-feature manual-login beta plus safe v3 onboarding QA**,
not a claim that the permanent automatic pairing feature is complete.
