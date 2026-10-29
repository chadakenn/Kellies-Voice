# Issue #193: B3 two-screen confirmation, developer QA

**Status:** Draft preview only. It verifies physical TV confirmation using ephemeral cryptography, but intentionally does NOT log in to Home Assistant, create an HA device entry, install an OAuth credential, or enable the microphone. Neither the public stable APK nor the current HACS integration has this unfinished feature.

## On an Android TV test APK

1. Open **Settings → Home Assistant → Preview no-typing pairing discovery (development only)**, then **START DISCOVERY PREVIEW**. This starts a two-minute temporary LAN listener on a real ephemeral TCP port.
2. The TV advertises **`_kellies-pair._tcp.local.`**, with the existing public `mode=discovery-only`, `protocol=1`, `session` and additional `preview=2` marker.
3. After installing the matching **development HACS custom integration** on a disposable HA test instance, go to **Settings → Devices & services** and select the discovered **Kellie's Voice setup** card. Explicitly choose **Start temporary verification**.
4. A new, temporary P-256 public key is sent from HA to the TV using a bounded `POST /v1/pair/offer`. The TV responds with its public key; neither side sends any URL, HA token, OAuth code or login.
5. **Both screens show an eight-digit code** derived from the shared ECDH secret and the ordered ephemeral public keys. Compare all eight digits physically. If they do NOT match, **Cancel immediately** on the TV.
6. If they match, select **THE EIGHT DIGITS MATCH - CONFIRM ON TV** using the TV remote. Back in HA, mark the checkbox and continue.
7. HA checks `GET /v1/pair/status?session=...`. Only the TV's explicit OK emits a 128-bit proof along with `tv_uuid`, the TV's existing stable v4 app UUID. The UUID is included inside the v2 transcript MAC (not just beside it in JSON), and HA validates the MAC using that UUID. An attacker changing `tv_uuid` without the proof key is rejected. If the TV hasn't approved, HA shows **waiting for TV**, and you can retry until the two-minute window ends.
8. The HA flow ends with **Verification preview complete**. It has NOT created a config entry or configured credentials. Your existing installation and manual fallback remain unchanged.

## Safety and security boundaries

- No private keys, HA credentials, device-registry IDs, saved URLs or login tokens appear in mDNS advertisements or HTTP responses. No internet or external service is contacted.
- The listener is only opened by visible TV button action and closes on cancel, Activity stop, time expiry, Android app restart and failed NSD registration.
- There is one valid peer offer per session; a second peer cannot silently replace the first code. Invalid key/role/curve/session, malformed JSON, oversized headers/bodies, unauthorized routes and expired requests are rejected. The connection window has a probe limit.
- Both sides must compare the eight-digit number. **ECDH proves key agreement but NOT that a network peer is the intended device**, so anyone who did not initiate a session must cancel. A denial-of-service attempt can consume the first offer. Never complete pairing only from an unverified mDNS discovery event.
- TV remote OK produces a domain-separated HMAC proof bound to the stable TV app UUID and ECDH transcript, not a string derived from or guessable from the visible eight-digit code. HA independently recomputes it with the same canonical UUID. The deterministic Java/Python v2 reference vector is `IQTCP6AIBcrvhq11xt54HA` for the test-only scalar pair and UUID `123e4567-e89b-42d3-a456-426614174000`; that value is never a reusable credential. The proof does **not** authorize Home Assistant or allow credentials to be provisioned.
- The discovered pairing preview is separate from legacy `_kellies-voice._tcp.` mDNS layout pairing and the existing manual HA login. The preview never calls `async_create_entry` and does not change `CredentialStore`.
- All network transport in this preview is intentionally limited to PUBLIC ephemeral key material and a public approval proof. **Never extend these endpoints to carry a bearer token, OAuth code, refresh token, HA URL, or anything confidential over HTTP.** The next PR requires an authenticated encrypted channel, confirmed transcript and revocable HA authorization first.
- This preview is accessible only in debug, signed dev or QA builds. Production stable settings do not show it.

## Owner hardware QA, not yet performed

- [ ] On the onn 4K Pro, discovery appears in a throwaway HA test instance without manually typing a URL or token.
- [ ] Both screens show identical eight-digit codes. Confirm with D-pad OK on TV; HA then verifies proof.
- [ ] Press HA confirm before TV OK: displays **Waiting for TV**, no config entry.
- [ ] Press Cancel or Back on TV while code is shown: HA verification fails, no config entry.
- [ ] Wrong code, forged public key, changed/missing TV UUID, replay, second offer, expired attempt, shutdown/standby, denied mDNS, and separate VLAN fail safely.
- [ ] The existing signed production TV app and matching live HA integration continue working, no lost settings/mic changes.
- [ ] Switching back to existing manual login remains possible.

## Next security stages

A future reviewed update must add OAuth owner authorization with HA-supported PKCE S256, a trusted encrypted credential provisioning channel bound to this exact transcript and physical TV OK, secure refresh/revocation and actual authenticated Assist WebSocket success before reporting Connected. It must keep the manual fallback and abort without touching an existing saved login on any failed step.

See [draft protocol details](PAIRING_VERIFICATION_PROTOCOL.md), [discovery-preview QA](PAIRING_DISCOVERY_QA.md) and [issue #193](https://github.com/chadakenn/Kellies-Voice/issues/193).
