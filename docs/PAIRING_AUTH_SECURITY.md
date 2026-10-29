# #193: Auth-safe zero-typing TV onboarding foundation

**State:** first security primitive only. No automatic pairing is implemented by this branch. The current Home Assistant integration can discover/add per-TV controls, but it **does not authorize Assist** or install a TV credential. Users still need the existing TV connection setup until the later secure protocol is reviewed and tested. This change does not start an open TCP port or enable a microphone.

## Verified Home Assistant authorization surface
Home Assistant documents native-app OAuth **authorization_code** login via `/auth/authorize` and `/auth/token`, supporting PKCE **S256** (the secure variant) and revocable refresh tokens. This is not a generic OAuth device-code grant and is not accomplished by an integration config flow just having a TV UUID.

References:
- https://contributors.home-assistant.io/core/internals/auth-api/
- https://contributors.home-assistant.io/companion/mobile-app-api/setup/
- https://contributors.home-assistant.io/core/internals/auth/

**Important permission limitation:** a refresh token is tied to a Home Assistant user; it does **not** automatically gain a special per-TV least-privilege scope. Owner consent and optional separate limited HA user should be designed explicitly. Never mint an admin token in a config flow or duplicate the current user's frontend token. Verify current HA capabilities before promising full least privilege.

## This first source step
`PairingAuthorizationSession` is a pure Java short-lived, one-attempt state/PKCE verifier helper, independent of Home Assistant and Android UI. It creates random 256-bit state and PKCE verifier values, emits the RFC 7636 S256 challenge, expires after two minutes, cancels explicitly and rejects repeated/invalid callbacks. It does **not** exchange tokens, start a local server, discover the TV or claim authentication. It must only be instantiated from an explicit future user action. JUnit tests verify the official RFC S256 vector and replay/expiry behavior.

## Next implementation PRs
1. **Permissioned TV discovery**: visible, explicitly initiated 120-second pairing window; fresh ephemeral key, real local endpoint only during that window; service TXT reflects real capability and never claims readiness when no listener exists. Existing normal discovery/manual path stays functional.
2. **HA config flow and owner login**: from Home Assistant UI, select a discovered TV, perform an explicit OAuth login with S256 on phone/PC, obtain an authorization code through a reviewed first-party callback, confirm short matching code on TV with one OK press. No token yet sent to unauthenticated or merely discovered devices.
3. **Encrypted local provisioning**: authenticated transcript binding, replay and timeout rejection, one-time transfer of authorized credential over an ephemeral ECDH/AEAD channel; preserve the old working TV credentials until authenticated Assist and alert handshake succeed. Ensure refresh/revocation behavior and prevent private HTTP credential exposure.
4. **HA-side finish only on success**: integration may report connected only after the intended TV authenticates. Tests: two TVs, wrong device, forged mDNS, stale callback, wrong confirmation code, restart, VLAN fallback, denial/revocation and debug/QA package isolation.

## UX contract
First run: **Waiting for Home Assistant**. On phone/PC: Add Integration → Kellie's Voice → select TV → authorize → compare code → one TV OK → Connected. No URL, bearer token or UUID typed on the TV. Until stages 1–4 have actual safe code and onn TV QA, the app must not label this as implemented.

## Forbidden shortcuts
No HTTP bearer token provisioning, automatic admin-token creation, device authentication by UUID/mDNS alone, unaudited remote listeners, compromised TLS exceptions, GitHub credentials/keys, wake-word activation, production credential overwrite, or claims that standalone PKCE makes pairing secure. See existing design draft PR #194 and issue #193.
