# #193 Stage B2: transcript-bound visual pairing code

**Status: cryptographic building block only; not a working automatic Home Assistant login.** Code is not yet wired into the temporary LAN discovery window or Home Assistant's config flow. It does not start services, authorize users, exchange OAuth codes, store credentials, or cause an Android microphone to start.

## Design

Each explicit onboarding attempt creates a new 16-byte random session ID (already present in the discovery preview) and **two ephemeral P-256 keypairs**, one generated on the TV and the other on the Home Assistant side. This curve is supported by Android's standard EC/ECDH APIs. Public keys are encoded as canonical X.509 SubjectPublicKeyInfo DER and bound to fixed roles: **TV key first, HA key second**.

Both peers independently calculate ECDH's shared secret. They construct a binary transcript:

`ASCII("KVPair/v1/transcript") || BE16(len(session)) || session || BE16(len(tvDER)) || tvDER || BE16(len(haDER)) || haDER`.

The session is exactly 16 bytes and represented externally by 22 base64url characters without padding; DER public keys must be valid P-256 curve points. The transcript must be in role order regardless of which side computes the value.

- `salt = SHA256(transcript)`
- `prk = HMAC_SHA256(salt, ECDH_SHARED_SECRET)`
- `okm = HMAC_SHA256(prk, ASCII("KelliesVoice/v1/visual-compare") || 0x01)` (HKDF single expansion block)
- `code = decimal8(U32BE(okm[0:4]) mod 100,000,000)`

Example **public test vector**: session `AQIDBAUGBwgJCgsMDQ4PEA`, P-256 TV private scalar 1, HA private scalar 2, derived comparison code **34821918**. Both Java and Python tests assert this vector and reject invalid role assignments, identical public keys, malformed sessions, non-P256 keys, and swapped participants. This is a deterministic test-only vector; never use these test private keys for a real session.

## Owner confirmation and binding are mandatory

This code is a **short authentication string (SAS)**. A network attacker can freely create their own ECDH keypair; ECDH **alone does not authenticate the endpoint**. The future protocol must display exactly the derived number on **both** Home Assistant and the intended TV. The owner visually compares all eight digits and presses a single physical OK on the TV. If numbers differ, either device times out, or the owner presses Cancel, the attempt MUST terminate without provisioning a credential. Do not accept a matching number transmitted back over the untrusted channel as proof of human comparison.

The visible pairing session must expire (two minutes), allow one pending initiator, rate-limit attempts, and not emit sensitive data in logs or mDNS. A compromised router, spoofed mDNS listing, or cross-TV relay must not be able to silently approve a session. The agreed transcript and confirmed session will also need to be used as additional authenticated data by a **future encrypted provisioning channel**, with a fresh unique AEAD nonce and replay protections.

## Home Assistant login is a separate step

Home Assistant uses OAuth authorization-code login and supports PKCE S256. The HA owner must initiate the flow from HA on a phone/computer and explicitly grant access. The client_id and redirect_uri must obey HA IndieAuth's supported callback restrictions. The current app's `CredentialStore` expects a URL and access token; a refreshable OAuth credential requires a separately reviewed token/refresh/revocation upgrade for `HaAssistClient`, TV alert receiver and other existing token consumers. Home Assistant refresh tokens identify a user; **do not describe them as automatically permission-scoped to a single TV**.

No URL, token, refresh token or OAuth code should be transferred to the TV until after (1) the correct user explicitly authorizes the correct HA instance, (2) the full visual + physical TV confirmation completes, (3) an authenticated encrypted transport is established, and (4) a failure can roll back without overwriting existing login. Finally, Home Assistant must only claim Connected after the intended TV proves an authenticated Assist WebSocket works.

## QA status / next step

This PR adds pure Java and Python cryptographic utilities and interop tests only. The next separate review must implement the **ephemeral key exchange over the read-only discovery listener's future authenticated handshake path**, visual matching-code UI, TV OK/CANCEL gating, timeouts, replay defenses and negative tests; never add token upload to the old unverified GET route. Physical on-device onn 4K Pro QA is required before any release to stable. See [#193](https://github.com/chadakenn/Kellies-Voice/issues/193) and [discovery-only QA](PAIRING_DISCOVERY_QA.md).

Official references: [Home Assistant OAuth/PKCE](https://contributors.home-assistant.io/core/internals/auth-api/) and [Android ECDH](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec).
