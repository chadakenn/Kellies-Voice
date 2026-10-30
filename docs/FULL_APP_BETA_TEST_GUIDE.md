# Kellie's Voice: Full App Beta Test Kit

**Development-only beta.** This builds the real Kellie's Voice Android TV app,
with the full Assist/camera/announcement/overlay/screensaver feature set and an
additional **Full app beta test center** in
**Settings → Home Assistant**, next to the debug-only v3 secure owner test.

## PR #260: Owner-approved automatic login beta

This kit now has a second, opt-in way to connect: verify the eight-digit
TV code, approve in the signed-in HA owner panel, and explicitly check
**dedicated ongoing TV login**. When Home Assistant Cloud HTTPS is enabled, the
TV uses that existing URL to receive its own renewable account and should
reconnect without a copied token. Cloud sign-ins require a remote-capable
dedicated account. **The standard non-admin Users role can control all
Home Assistant entities**, so approve only for a trusted TV.

Read `OWNER_APPROVED_LOGIN_TEST.md` in the kit before trying this path.
The manual token setup instructions below still apply if you skip that
option or are testing the older beta behavior.

## Read this before installing

- The beta APK uses a **separate Android application ID**
  `com.example.tvvoiceprobe.debug` and appears as **Kellie's Voice Beta**.
  Your existing signed stable app `com.example.tvvoiceprobe` remains installed
  with its settings/login intact. Do **not** uninstall or clear data for the
  stable app.
- Beta storage is **separate**. On PR #260, owner-approved v3 pairing can
  provision a dedicated renewable TV login automatically when the HA owner
  explicitly selects the standard-user login opt-in. Manual login is a
  fallback for testers who deliberately do not use that beta path.
  **Never share a token in screenshots, GitHub, or chat.**
- An eight-digit matching code or owner receipt **alone** does not prove
  successful login. Verify the completed activation and actual Assist
  `CONNECTED AND AUTHENTICATED` status separately. The dedicated ordinary
  HA Users account is not entity-scoped and can control all HA entities.
  The stable app and its settings remain independent.
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
2. Review live readiness. After successful owner-approved v3 login, reopen
   the beta and verify **HOME ASSISTANT: CONNECTED AND AUTHENTICATED**
   without pasting any credentials. If you intentionally skipped ongoing TV
   login, the separately stored manual URL/token flow remains available.
3. Press TALK, ask one ordinary Assist question, confirm recognition, voice
   reply, and correct TV output. Repeat to test conversation continuity.
   **For targeted alerts and camera popups**, open the beta TV's
   **Settings → Home Assistant → Manage TV layout from Home Assistant**
   and enable it, then in HA **Settings → Devices & services** add the
   *normal* **Kellie's Voice** integration discovery for the **beta TV**.
   This is a separate Android app with a separate TV pairing ID from stable.
   Confirm the new beta TV device/alert controls exist in the normal
   HACS integration and target this beta device for tests. **Do not**
   select **Kellie's Voice v3 SAFE TEST** for ordinary alerts: that isolated
   integration deliberately creates no usable devices.
4. Open the floating voice overlay. Confirm close/Back and that the mic stops
   when the overlay is dismissed. Check manually disabled mic remains blocked.
5. In **Settings → Voice & microphone**, choose **Android microphone
   permission: GRANT NOW**, then approve Android's prompt. This step does
   **not** begin recording. If Android stops showing the permission request,
   choose **Open Android app permissions**. In **Alerts & cameras**, switch
   **TV notifications & camera popups ON**, grant **Display over other apps**
   when prompted, and return to the app. The beta resumes the explicit
   alert-enable action after the system permission succeeds. If Android
   doesn't expose the special-access panel, use TV Settings → Apps →
   Special app access → Display over other apps for **Kellie's Voice Beta**.
   Then open **TV alert status & connection**, choose Resume if needed.
   A separate beta-TV device must exist in the **normal** Kellie's Voice
   HACS integration. The standalone v3 QA integration only approves login.
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

- Independent review of the new dedicated non-admin Users-account permissions
  (not least-privilege, because ordinary Users can control all HA entities)
- End-to-end restart, renewable token refresh, revocation, and recovery tests
- Whole-Assist and alert authorization enforcement, not only a QA permission
  checker
- Independent security review (#214), 2-TV isolation, malicious LAN peer QA
- Production signing, migration and actual release verification

This kit is an **isolated full-app automatic-login beta** with an optional
manual-login path. Successful code/owner approval does not establish complete
reconnect, revocation, or alert-delivery readiness; keep PR #260 in draft.
