# Real v3 code and physical remote test: Home Assistant flow (#193)

This is a **draft, token-free test** built atop v3 Android debug listener
#245 and TV debug screen #246, and HA bounded LAN initiator #244.
Existing stable Kellie's Voice entries, user's manual-token connection,
mic, cameras, timers and wake-word configuration are untouched.

## What the Home Assistant UI now supports

With this exact draft HA integration and Android debug TV app installed
on a **staging** instance:

1. On the TV: **Settings → Home Assistant → Secure pairing v3 (DEBUG
   TEST ONLY, no login)**. Press **START 2-MINUTE V3 TEST**. This is the
   only time the listener/mDNS starts, for at most 120 seconds.
2. HA automatically discovers the v3 advertisement
   `_kellies-pair._tcp.local.`, `protocol=3`,
   `mode=verified-code-only`, plus the exact ephemeral session.
   It does not trust a permanent TV UUID or owner ID from discovery.
3. In HA **Settings → Devices & services**, select the new **Kellie's
   Voice secure pairing v3 TEST** discovery and explicitly opt in.
   The TV/HA use real ephemeral P-256 key pairs and both SHA256
   commitments before revealing either key/nonce.
4. HA and TV display freshly derived v3 eight-digit numbers. The HA
   form contains the number in a required prefilled field, so missing
   translations cannot leave users approving an invisible number.
5. Compare ALL EIGHT digits, then press OK on the physical TV remote.
   Back in HA check `numbers_match` and continue. HA verifies the
   v3 UUID-bound remote proof using its own ECDH secret; while waiting,
   the HA form shows a retryable waiting message.
6. Success says **TV verified successfully using secure v3**.
   It is a verification-only **abort**, not a config entry or
   actual TV account authorization, token, Assist or alert login.

## Fail-closed rules

- Legacy stable `_kellies-voice._tcp.local.` keeps its existing config
  entry path. The v3 discovery path requires exact protocol 3, mode,
  canonical session and a selected private literal IPv4 address/port.
- Unknown, v1/v2, malformed or replayed v3 packets never trigger a
  credential handoff or downgrade.
- The network is not contacted until the person opts in through the HA
  setup form. No HA user ID, OAuth code or token is accepted from the TV.
- Missing/mismatched eight-digit numbers abort instead of silently
  accepting physical TV OK.
- Wrong user, wrong TV, stale listener and unverified proof must abort.
  `preview_verified` or `v3_preview_verified` is not a login.
- A separate v3 discovery flow is identified by `v3-preview:<session>`
  and never creates a persistent TV config entry.

## Before hardware use

**Do NOT overwrite the user's existing live HACS integration with this
QA branch.** Only use a staging HA instance or an explicitly backed-up
test environment. The Android debug package uses the separate
`.debug` application ID, avoiding replacement of the stable TV APK.

The debug APK still needs successful CI, and HA Core 2026.10.2 must be
tested with its real Zeroconf discovery object/flow translations. Real
network tests on the onn 4K Pro require both matching branches deployed.
No credentials are involved in this hardware test.

## What still blocks the actual v0.17.0 auto-login release

Finish same-session signed-in owner approval (#241), implement separate
truly least-privileged TV identity (#239), use crash-safe token intent
and offline revocation (#240), add a reviewed session-bound encrypted
credential delivery channel, atomic Android Keystore promotion, real
Assist/TV alert authorization tests and recovery/Disconnect lifecycle.
Independent security review is mandatory before enabling bearer tokens.

No public release, main merge or installed user change in this draft.
