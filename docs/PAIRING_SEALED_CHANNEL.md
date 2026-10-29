# #193 Stage B4: authenticated sealed transport primitive (not live)

**Development draft only.** The preceding PRs #197, #200, #201 and #202 establish OAuth/PKCE session helpers, discovery, ECDH short code and owner TV-OK proof. This stage adds the **encryption primitive** that a later, separately reviewed provisioning path may use. It is **not connected to the pairing listener, HA config flow, CredentialStore, Assist or camera WebSocket** and cannot currently provision an access token.

## Protocol v1

Both sides must first complete the exact same two-minute ephemeral pairing attempt and visibly match the eight-digit code. HA must independently verify the TV's OK proof, and the HA user must separately approve authentication through its supported login flow. **Neither the preview code nor a single boolean is sufficient to authorize confidential provisioning.**

The channel uses the existing, validated ephemeral P-256 keys, fixed-role canonical DER public keys and 16-byte session ID:

- `transcript = "KVPair/v1/transcript" || length16(session) || session || length16(tvDER) || tvDER || length16(haDER) || haDER`
- `salt = SHA256(transcript)`, `secret = ECDH(private, peerPublic)`, `prk = HMAC-SHA256(salt, secret)`
- `haToTvKey = HMAC-SHA256(prk, "KelliesVoice/v1/channel/ha-to-tv" || 0x01)`
- `tvToHaKey = HMAC-SHA256(prk, "KelliesVoice/v1/channel/tv-to-ha" || 0x01)`
- Each direction uses AES-256-GCM with a fresh 96-bit `SecureRandom`/`os.urandom` nonce per outgoing packet, a 128-bit tag, and maximum 2,048-byte plaintext. There is no persistent key, shared master password or TV access token involved at this stage.

Wire format: `0x01 | direction[1] | sequence[uint64 BE] | nonce[12] | ciphertext | gcmTag[16]`, with direction 0 = HA to TV, 1 = TV to HA. Additional authenticated data is `"KelliesVoice/v1/channel-aad" || SHA256(transcript) || full header`.

A receiver accepts only the **next expected sequence number in its own receive direction**. Any replay, packet reordering, reflection, modified header, modified payload, wrong peer or session, invalid tag or unsupported version fails without yielding plaintext or incrementing the receive counter. Closing destroys the in-memory session's keys to the extent supported by runtime memory APIs. A new ephemeral session is required after expiry or disconnect. Neither implementation writes keys or messages to logs or disk.

Cross-language deterministic test vector, using **public test-only private scalars 1/2**, session `AQIDBAUGBwgJCgsMDQ4PEA`, direction HA→TV, counter 0, nonce bytes `000102030405060708090a0b`, plaintext `PAIRING PREVIEW ONLY`:

`01000000000000000000000102030405060708090a0bf29a8b99db35f1a23b424b1c0cf5a5a3ecbd7cf98b39cbbc68e2ae4b14808ff445d0c78a`

This fixed nonce and these fixed private keys are **never** for production. No real credentials or URLs appear in any test fixture.

## Safety gates before actual token provisioning

1. Independent review of secure channel cryptography, key lifecycle, nonce reuse assumptions and constant-time proof checks.
2. Gate construction and use of the channel behind both confirmed **physical TV OK** and an authenticated Home Assistant user's explicit OAuth PKCE authorization; bind the exact home-assistant origin and intended TV device to the authorized transcript.
3. Introduce a **new**, strict encrypted provisioning endpoint that accepts only authenticated AEAD envelopes and only while a single, owner-confirmed pairing window is active. Never send a token or HA URL over the unencrypted discovery/offer/status routes.
4. Implement minimal, revocable HA authorization and refresh-token storage with appropriate client ID, redirect and expiry, and an atomic rollback that preserves any existing valid TV login.
5. Require an authenticated Assist WebSocket proof before showing Connected or creating a persistent paired entry. Validate mDNS impersonation, multi-TV, same/different VLAN, failed login, timeout, replay and app/HA restarts on test hardware.

This stage doesn't open a new port, alter any HA user, create a config entry, change a signed APK, auto-start the microphone, or bypass the existing manual login fallback. Review [#193](https://github.com/chadakenn/Kellies-Voice/issues/193) and [two-screen preview QA](PAIRING_TWO_SCREEN_QA.md).
