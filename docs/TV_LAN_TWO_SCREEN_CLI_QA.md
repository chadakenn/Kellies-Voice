# Kellie's Voice: verify actual LAN pairing on onn TV without changing Home Assistant

The **Pairing Lab test APK** from PR #227 already passes seven local
encryption checks and starts the 120-second discovery listener. The matching
development Home Assistant pairing flow is still draft-only: current
production `custom_components/kellies_voice/manifest.json` advertises only
`_kellies-voice._tcp.local.`; the TV Pairing Lab advertises
`_kellies-pair._tcp.local.`. Merely pressing Start on TV will NOT produce an
eight-digit code unless a peer actually sends an offer.

This test uses a **standalone disposable Python program**, instead of
installing the unreviewed OAuth stack on your working Home Assistant server.
It requests only the public capabilities and public P-256 key, displays an
eight-digit code, and verifies the physical TV remote OK with ECDH v2. There
are **NO Home Assistant URLs, authentication tokens, OAuth browser prompts,
stored credentials, config entries, microphones or saved settings**.

## Windows setup (one-time)

Open PowerShell on a PC on the same home LAN/VLAN as the onn TV. Install
Python 3 if it isn't present, then:

```powershell
py -m pip install "cryptography>=42,<47" "zeroconf>=0.130,<1"
```

Download this exact source file from the draft repo:

[tools/pairing_preview_cli.py](https://github.com/chadakenn/Kellies-Voice/blob/feature/isolated-lan-pairing-preview-cli/tools/pairing_preview_cli.py)

Save it locally as `pairing_preview_cli.py`, for example in Downloads.
You can verify what it does before running it. It is not an executable APK
and never sends credentials.

## Test

1. Leave the **Kellie's Voice Pairing Lab** debug APK installed, preserving
   the normal working TV app.
2. On TV, go to **OPEN LAN TWO-SCREEN CODE PREVIEW**.
3. On the PC, open PowerShell in the directory containing the script.
4. On TV, press **START DISCOVERY PREVIEW**.
5. Immediately on the PC run:

   ```powershell
   py .\pairing_preview_cli.py
   ```

6. The script should discover `_kellies-pair._tcp.local.` and print an
   eight-digit code. **That exact code should also appear on the TV**.
7. **Only if all eight digits match**: on TV press
   **THE EIGHT DIGITS MATCH - CONFIRM ON TV**, then press Enter on the PC
   to verify the authenticated TV-OK MAC.
8. Expected terminal result:

   ```text
   PASS: Physical TV remote OK and P-256 ECDH TV identity verified.
   NO Home Assistant account was authorized or changed.
   ```

9. Exit using **CANCEL / BACK** on the TV; confirm the temporary listener
   stops. If the 120-second window has expired while preparing your PC,
   simply press Start on the TV again and rerun the script.

## If it does not discover the TV

- Both the PC and the TV must be able to exchange multicast DNS/Bonjour
  packets and directly connect over the private LAN.
- If your PC is on one VLAN and the onn is on another, mDNS reflection and
  local firewall rules may block the discovery. The program intentionally
  refuses public IP addresses, user-supplied hostnames, non-private IPs, and
  redirect URLs.
- The script does not accept a manual public URL or an OAuth token as a
  fallback, and should not be exposed to the internet.
- If a code is shown on only one screen or the digits differ, **cancel both
  sides**, never press Confirm.

This test advances beyond an on-TV self-test to verify that a real second
device can exchange the public transcript and validate TV remote OK. It
still does **not** test Home Assistant OAuth consent, real token handoff,
the true HA Assist+TV-alert WebSocket authorization, Keystore activation,
refresh tokens, or crash-safe revocation.

The unfinished Home Assistant draft integration must remain uninstalled
on the production server until the credential lifecycle and security review
#214 are complete.
