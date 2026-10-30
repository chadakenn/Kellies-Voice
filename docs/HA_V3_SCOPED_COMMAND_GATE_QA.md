# V3 TV-specific capability gate (offline preview)

**Draft security prototype only. No new authentication, credentials, LAN
endpoint, Home Assistant services, TV APK update, or live commands.**

The previous physical onn 4K Pro tests proved the TV's v3 ECDH/remote-OK
identity plus the signed-in HA owner's one-use approval. That is a solid
identity signal but it is **not yet** a secure, ongoing TV session.

## Scope of this PR

`scoped_tv_gate.ScopedTvGate` is an offline, process-local
authorization-decision prototype, stacked on #253.

- Binds a synthetic *server-side connection object* and a matching v3
  `VerifiedTv` and narrow `TvScopeProof`. UUID and owner-name strings
  alone never authorize any request.
- Applies only a small action vocabulary: `alerts.subscribe`,
  `entity.read`, explicitly approved `entity.control` (light, fan,
  media player only), and explicitly approved `camera.snapshot`.
- Denies generic `call_service`, all unrecognized operations, direct
  `assist.execute`, locks, alarms, scripts, automations, covers,
  switches, wrong-TV/wrong-session access, out-of-scope entities,
  duplicate channels and unapproved cameras.
- Enforces a per-connection monotonically increasing request sequence,
  finite expiry of at most 120 seconds, and immediate disconnect/restart
  revocation without waiting for a TV response.
- Does not call Home Assistant services, expose an HTTP or WebSocket
  endpoint, issue or accept a bearer token, enroll a user, or save a grant.

## Critical architectural boundary

The input `VerifiedTv` is a Python data object and **cannot itself
authenticate a remote TV**. The entrypoint must never be offered directly
over HTTP/WebSocket or used with any client-provided connection ID. A
future signed owner-approval result must be bound to an authenticated
channel using a newly reviewed ECDH transcript-bound AEAD/MAC transport.
The current Android debug QA implementation closes its v3 ECDH state after
physical OK and therefore cannot currently authenticate ongoing messages.
The scope policy here is only an **offline executor-side authorization
decision**, not working login, not an active credential, and not protection
against a peer able to spoof the authenticated connection.

Do not connect the gate to stable `kellies_voice`, mint HA OAuth tokens,
or claim a token-free TV is ready for Assist. Before integration, add:

1. A real HA-side owner approval receipt bound to the exact v3 TV UUID,
   ephemeral session and intended server connection. Revalidate the HA
   owner on use, not a boolean returned from the browser.
2. A v3 authenticated message transport (HKDF-split directional session
   keys, replay resistance, expiry, connection identity and device
   reconnect). Authenticate *each* message rather than trusting a UUID.
3. An executor with per-capability, per-entity checks plus independent
   Home Assistant's actual entity and service permissions. Sensitive
   physical operations must require additional explicit owner setup and
   never fall through to arbitrary Assist intents.
4. Real testing of camera privacy, authorized alerts, scoped Assist,
   locks/garage rejection, multi-TV isolation, expired sessions, TV/HA
   restart, token revoke/rollback and #214 independent security review.

## Run checks

`python -m unittest discover -s tests -p 'test_v3qa_scoped_tv_gate.py' -v`

The isolated QA package includes this offline policy module for code
review, but the active v3 QA config flow remains unchanged and must still
end with `owner_verified_no_login`. Nothing in the installed app starts
calling the new gate.
