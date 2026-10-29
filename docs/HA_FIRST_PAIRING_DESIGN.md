# Home Assistant-first TV pairing design (proposal)

Status: **design only; not implemented or security-reviewed.** Tracks [issue #193](https://github.com/chadakenn/Kellies-Voice/issues/193). Keep the legacy manual login and debug/release pairing behavior intact until the protocol has independent review, tests and physical QA.

## Product contract

Install and launch Kellie's Voice on Android TV. The TV says **Waiting for Home Assistant**; it must not ask for a server URL, username, access token or a pairing ID. On a phone/PC already logged into Home Assistant, navigate to **Settings → Devices & services → Add integration → Kellie's Voice**, select the discovered TV, and press **Connect**. After confirming a secure pairing, the app saves its own revocable connection credentials and proves that authenticated Assist and (if explicitly enabled) TV alert WebSockets work.

Remote control should require at most a **single OK to approve** with an easy-to-read matching code; no on-TV typing. A truly zero-touch approval mode is not in scope until its security has been independently justified. Failed/aborted pairing does not erase an existing working login.

## Existing implementation and gap

- `HaDiscovery` advertises `_kellies-voice._tcp.` with `id` and `name` TXT attributes, plus a placeholder **45600** port. **No server listens there**. The advertised UUID is an identifier, never an authentication proof.
- The HA `config_flow.py` confirms discovered metadata (or manually entered UUID) and creates per-TV entities. It makes no live contact or authentication check.
- `CredentialStore` stores URL and an HA bearer token; `VoiceActivity`, `HaAssistClient`, and `TvEventsService` each use saved credentials for authenticated HA WebSockets. The TV currently needs separate manual setup.
- A debug APK has a different Android application ID and credential/pairing storage. Do not replace production credentials or reuse IDs implicitly.
- HA itself advertises `_home-assistant._tcp.local.` over zeroconf, so the address can be discovered rather than typed. This is a transport hint, not permission to authenticate.

## Confirmed authorization facts (October 29, 2026)

Home Assistant's documented native-app authentication is OAuth 2 **authorization code plus PKCE S256**, with `/auth/authorize`, `/auth/token`, short-lived access tokens and separately revocable refresh tokens. This is **not an OAuth device-code grant**. Native custom callback URIs require registration from the app's `client_id` web page (or supported client metadata). The implementation must decide how a logged-in HA owner completes browser authorization on a PC/phone and how the response reaches the *correct* TV via a **cryptographically verified local pairing channel**. Simply collecting a code from mDNS or a config flow is insecure.

An HA refresh token represents its linked user, **not automatically a per-TV permission-limited scope**. Do not claim that a custom integration can silently issue device-only admin-restricted tokens; first verify the installed Home Assistant permission and user model, get deliberate owner consent, and make revocation obvious. The only committed implementation so far is the standalone short-lived PKCE/state helper in draft [PR #197](https://github.com/chadakenn/Kellies-Voice/pull/197); it performs no credential exchange or LAN listening.

Official references: [Authentication API](https://contributors.home-assistant.io/core/internals/auth-api/) and [Native app login](https://contributors.home-assistant.io/companion/mobile-app-api/setup/).

## Candidate protocol, with explicit security gates

1. **Pairing intent**: only a visible first-run/onboarding activity starts a short-lived pairing session and local listener. Create a fresh unpredictable session ID and ephemeral key pair; show `Waiting for Home Assistant` plus the device name. Lifetime e.g. **120 seconds**, renewed only by explicit user action. Pairing advertisement is removed after success/cancel/timeout.
2. **Discovery**: advertise pairing capability, session identifier, version and public-key fingerprint via TXT. Never put an access token, password, URL containing credentials, or long-term private key in TXT. HA uses its shared zeroconf discovery implementation and must reject stale/ambiguous sessions.
3. **Initiation from HA**: a logged-in HA owner deliberately selects the TV and presses **Connect**. HA opens a transport to the endpoint that actually exists (not the former placeholder port), negotiates a short-lived encrypted channel, and identifies the intended HA instance.
4. **Bind the peer**: show a short authentication comparison value derived from the transcript on the HA setup page and the TV; **compare visually, then approve with OK** on the TV. This is not typing. Reject differing codes, stale sessions, replay, concurrent competing requests and IP/identity substitution. mDNS is spoofable and alone does *not* authenticate either endpoint.
5. **Authorize access**: use a supported HA authorization flow with deliberate user consent, revocable **per-device credentials** and least possible permission scope. **Open decision**: prove feasibility of user-approved native OAuth/PKCE with a secure transfer of a one-use code, versus an HA frontend-mediated `auth/long_lived_access_token` action explicitly initiated by the current user, or a permission-limited service identity. Do not mint an admin token in a background config flow, copy the owner's existing token, invent scopes, or assume a flow exposes a current user identity. Auth approval and per-device revocation semantics must be documented and tested *before* transferring secrets.
6. **Provisioning**: encrypt any credential end-to-end to the TV's verified ephemeral key with authenticated encryption (e.g. X25519 + HKDF + AES-GCM or a reviewed TLS pairing channel); use fresh nonces and explicit key confirmation. Supply the HA base URL from a verified HA origin, with HTTPS handling and clear local-network constraints. TV validates the URL, stores authorization in the existing secure credential store, performs its standard authenticated websocket handshake and reports success. The HA UI must not claim Connected until the TV proves it.
7. **Commit or rollback**: only after TV confirms an authenticated connection, finish the HA config entry or link to the existing matching entry. Cancel/timeout/failed auth revokes issued credentials, drops ephemeral secrets and listeners, and retains any previous valid setup. Re-pair explicitly replaces only the targeted device identity.

## Threat model and invariants

- An attacker on the same Wi-Fi can spoof zeroconf metadata, relay unauthenticated HTTP, initiate pairing requests or replay old packets. Pairing codes + physical consent and transcript binding protect against a token being issued to the wrong peer. If HA cannot reach the TV because of VLAN/mDNS, explain it and keep legacy manual connection as a fallback.
- No publicly reachable listener, cloud relay, global port-forward, automatic admin privilege or microphone/wake-word activation. The local listener is available only during visible onboarding. Strict origin/IP validation, rate limiting, per-session timeout and one-session-at-a-time behavior are mandatory.
- No credentials appear in logs, error reports, entity attributes, automation traces, AppBlock/UI state, filenames, docs or exception text. Test that cancel/failure leaves no keys, refresh tokens or partial config entries.
- No arbitrary URLs copied from mDNS directly into HTTP/HA credentials. Guard against SSRF, DNS rebinding and host changes. No unverified remote certificate exceptions.
- Existing manual credentials are not migrated or deleted without explicit intent. Never allow a second pairing to silently control an already paired TV.

## Small independent implementation slices

- **Protocol/design review** (this document only): decide and validate HA auth method, threat model, same LAN/VLAN limitations and UX.
- **Discovery-only tests**: implement a finite pairing state machine and read-only server discovery; zero credential writes, no active listener, no production behavior change.
- **Android local pairing service**: intentionally scoped ephemeral listener and TV approval, lifecycle and replay tests; no tokens until auth is reviewed.
- **HA config flow**: user-approved authorization, encrypted provisioning, idempotent config entry creation with retries, mocked HA core tests.
- **Hardware acceptance**: onn 4K Pro factory-fresh test package; HA add → cryptographically verified pairing → actual authenticated Assist; optional scoped alert receiver ON; HA restarts; app reboot; cancel; expired/invalid credentials; two TVs with distinct IDs; cross-VLAN rejection; revoke/re-pair; no typing on TV.

## Release safeguards

- #193 is tracked independently of UI/theme draft #192. Do not merge a partially working pairing UX into the stable app.
- Do not weaken the existing manual pairing protections, authenticated scoped alert subscription, microphone safety or encrypted `CredentialStore`.
- Test on a debug package with known signing provenance before attempting in-place upgrades. GitHub-hosted ephemeral debug keys can make replacement fail and cause users to lose debug-only pairing configuration.
- Record only non-secret diagnostic stages: `discovered`, `awaiting_approval`, `authorized`, `provisioned`, `authenticated`, `failed` with reason code and no credentials or full identifiers.

### References

- Home Assistant contributor documentation: [Instance discovery](https://contributors.home-assistant.io/core/internals/instance-discovery/), [Discovery](https://contributors.home-assistant.io/core/integrations/discovery/), [Authentication API](https://contributors.home-assistant.io/core/internals/auth-api/) and [How authentication works](https://contributors.home-assistant.io/core/internals/auth/).
