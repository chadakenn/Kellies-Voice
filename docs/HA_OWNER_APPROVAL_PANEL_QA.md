# Owner approval UI test: Home Assistant-native and token-free (#235)

**Draft, stacked on #234 owner-bound WebSocket authentication. Do NOT install
into the owner's live HA or merge until real HA Core + browser QA.** This is a
UI proof, not working account pairing.

## Why this is needed

HA's built-in Zeroconf configuration flow does NOT reliably supply an
authenticated `context.user_id`, so the public discovery card cannot be
used to determine which person has approved a TV. Asking the person to
type an ID or copy an admin token would be unsafe.

#234 provides a backend command that uses actual `connection.user` in a
**signed-in Home Assistant WebSocket**. This adds a visible, owner-only UI
to call it from the same HA session.

## QA-only manual sequence

1. Open the Pairing Lab or first-run TV app. Press START for a two-minute
   temporary discovery window. Nothing is listening before START.
2. HA finds the TV. Start setup, compare all eight digits **on both screens**,
   press OK with the actual TV remote, check the matching-digits confirmation.
   The backend must verify actual P-256 ECDH and TV UUID-bound remote proof.
3. The config flow shows a clearly marked TEST ONLY consent screen. **Leave
   that dialog active.** From Home Assistant's existing signed-in browser,
   open **Kellie's Voice Pairing** in the sidebar.
4. The custom panel lists only *already TV-verified* and not-yet-claimed
   pairing sessions, with device UUID and expiry. It uses
   `hass.connection.sendMessagePromise` over the browser's authenticated
   WebSocket. If a TV is not shown, do not manually add a user ID.
5. Click **Approve this TV** only for the exact TV you initiated. Backend
   checks `connection.user.is_owner` + `is_active` and binds to exact
   `flow_id` + TV session. Wrong owner, expired session and repeated approval
   are denied. This is a one-use, process-local claim.
6. Return to the original HA setup dialog to continue the still token-free
   browser callback preview. This stage does NOT exchange `/auth/token`,
   store a credential, modify the existing HA entries or sign in the TV.

**Current UX gap:** Switching to the sidebar may close the original HA
config-flow modal, and a 120-second TTL can expire. It is therefore not yet
the promised single-flow public onboarding. The next iteration must wire
panel approval back to the same flow via a supported external-step or native
frontend mechanism. This is a deliberate QA blocker, not a product claim.

## Security constraints

- Custom panel is served from the installed integration's bundled local
  JavaScript and registered with `require_admin=True`. The WebSocket handler
  *separately* rejects admins who are not owners and inactive users.
- The server never trusts `user_id` from TV, TXT, client JSON or config
  flow context. Only `connection.user` is authoritative.
- The list contains flow ID, public TV session, UUID and TTL. No bearer,
  refresh token, authorization code, device key, secrets, or browser storage.
- The panel renders server-provided text using `textContent`, never
  interpolating it into HTML. Only static markup uses `innerHTML`.
- Missing TV-OK proof, wrong session, replay and timeout abort safely.
- Panel URL path is local to HA (`/kellies-voice-pairing`); it works with an
  HA session opened locally or through an existing Nabu Casa HTTPS URL.
  A paid subscription must NOT be required for the ultimate product.
- The experimental callback #231 still discards authorization codes; no TV
  credential can be generated here.

## Tests and release blockers

`test_pairing_owner_claim.py` tests owner-only pending list and claim,
nonowner denial, no owner IDs or secrets sent in the list, after-claim
disappearance and one-time registration of both WS handlers.

`test_pairing_owner_panel.py` checks actual bundled asset registration,
admin-only UI and forbidden browser credential handling. Dedicated CI runs
these along with pairing crypto, ECDH, HA flow and Node JS syntax checks.

Still blocked: supported one-modal HA frontend flow, H2 v3 commit/reveal
wire deployment (#236), H1 non-admin TV credential proof (#239), H4 durable
token journal (#240), true Assist + TV-specific alert permissions,
secure encrypted TV delivery, Android Keystore promotion, restart/revocation
and independent security review. All drafts remain unmerged.
