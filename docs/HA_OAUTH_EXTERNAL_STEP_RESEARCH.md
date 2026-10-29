# Home Assistant same-origin IndieAuth OAuth external step research

**Issue:** #193  
**Date:** 2026-10-29  
**Purpose:** Verify the actual browser-based authorization flow pattern for a custom config flow using Home Assistant's IndieAuth OAuth, external steps, and callback registration. No real tokens, no implementation of credential exchange, no main/dev/release changes.

## Official sources (2026.10 / 2026.11 era)

- [Authentication API](https://contributors.home-assistant.io/core/internals/auth-api/)
- [Flow steps (external step)](https://contributors.home-assistant.io/core/integrations/flow-steps/)
- [Always pass a step_id to flow results (2026-10-23)](https://contributors.home-assistant.io/blog/2026/10/23/flow-results-require-step-id/)
- [OAuth 2.0 helpers](https://contributors.home-assistant.io/core/integrations/oauth2/) (primarily for cloud services; useful reference)
- Core source patterns in `homeassistant/data_entry_flow.py` and `homeassistant/helpers/config_entry_oauth2_flow.py`

## Required flow pattern

```text
async_external_step(step_id=...)
  → browser opens HA /auth/authorize (IndieAuth OAuth + PKCE)
  → authenticated HA owner consent
  → HomeAssistantView callback endpoint
  → hass.config_entries.flow.async_configure(flow_id, user_input=...)
  → async_external_step_done(next_step_id=...)
```

### 1. `async_external_step` (mandatory `step_id`)

As of the 2026-10-23 change, **`step_id` is required** on `async_external_step` (and on show_form / show_menu / show_progress). Omitting it will eventually raise in core and currently warns for custom integrations.

```python
return self.async_external_step(
    step_id="authorize",          # required; name of the step method without async_step_
    url=authorize_url,            # URL carries an unpredictable, expiring OAuth state
)
# IMPORTANT: the OAuth state must be an independent cryptographically random
# nonce, stored server-side with flow_id, authorized owner and verified TV.
# Never put a bare flow_id in the state parameter.
```

### 2. Browser authorization

Redirect the user to the HA instance itself:

```
https://<ha-origin>/auth/authorize
  ?client_id=<client_id>
  &redirect_uri=<redirect_uri>
  &state=<flow_id or bound state>
  &code_challenge=<S256 challenge>
  &code_challenge_method=S256
```

- Client ID is an IndieAuth URL. Using the HA origin itself as *this custom app's* client ID is a **proposal**, not proof HA accepts that identity; verify with actual HA authorization and token endpoints, particularly redirect URI constraints. Do not impersonate the companion app.
- Redirect URI must be permitted by the client identity, and match the value used in token exchange. The same-origin HA callback is a proposal subject to live verification.
- PKCE S256 is required for this native / non-confidential client design, but PKCE does not replace random state, user consent, TV proof or token-channel authentication.
- Owner must be authenticated and explicitly consent. Never silent. The public callback itself is *not* automatically authenticated merely because an owner used HA's frontend to initiate the flow.

### 3. Callback (`HomeAssistantView`)

The integration registers a view (or uses a known path) that receives the redirect with `code` and `state`.

**Security design sketch (not copy-paste-ready code):**

```text
HA owner starts explicit pairing, after the TV ECDH/code/OK proof
  -> create unpredictable random OAuth state (NOT the flow ID)
  -> server-side bind: state, flow_id, authenticated owner identity,
     verified TV identifier, ECDH transcript, expiry, single-use marker
OAuth redirects to same-origin HomeAssistantView callback
  -> callback may be public to receive OAuth redirects
  -> accept only canonical exact state, code and expected callback origin
  -> atomically consume still-live state; independently validate bound
     owner/session + authorization and TV proof
  -> pass only the validated code/state to the exact saved flow via
     hass.config_entries.flow.async_configure(flow_id, user_input=...)
  -> never issue secrets in the callback's response or its logs
  -> any failure aborts and revokes any token issued on a failed attempt
```

A `HomeAssistantView` with `requires_auth = False` is an **unauthenticated HTTP endpoint**. It cannot infer the initiating HA user from the redirect or trust a query-string `flow_id`. Look up the server-side state and verify the initiating authenticated owner and pairing context. Even after authorization, a returned code does not establish that the intended TV received credentials or that Assist connected.

**Important:** Verify whether a custom integration can safely associate browser consent with the initiating HA owner identity using supported HA APIs. If that ownership guarantee cannot be established, do not implement a public callback based solely on a random state value. Fail closed and choose a supported authorization route.


### 4. Resume the flow

```python
async def async_step_authorize(self, user_input=None):
    if user_input is None:
        # first call – show external step
        return self.async_external_step(step_id="authorize", url=...)

    # second call – the callback validated the random state and bound
    # owner/TV/flow context; the code has not yet been exchanged.
    # The actual token exchange and encrypted TV provisioning must still
    # independently validate consent and authentication, then:
    return self.async_external_step_done(next_step_id="exchange")
```

Work with the code happens in the *next* step, not inside the callback view.

### 5. Local HTTP / HTTPS / reverse-proxy considerations

- Client ID and metadata documents must be HTTPS when using the metadata document form.
- Same-origin redirect URIs work with both local HTTP (`http://homeassistant.local:8123`) and HTTPS.
- Reverse proxies must preserve query parameters and the original host (or correctly set `X-Forwarded-*` / `HA-Frontend-Base`).
- Loopback HTTP is acceptable for local HA; public LAN plaintext HTTP is not for token endpoints.
- **Identity warning:** the browser owner who approves OAuth is not automatically identifiable to a public callback. Record a server-side owner/flow binding when the flow begins, recheck the returned state, and prove which authorized HA user issued the credential before provisioning it. Do not assume the callback request retains the HA frontend's session.

## What this research does *not* do

- Does not register a real callback view.
- Does not exchange codes or store tokens.
- Does not mint admin tokens or copy existing long-lived tokens.
- Does not treat mDNS discovery as authentication.
- Does not modify main, dev, or any release branch.

## Recommended next implementation step

Wire a minimal, owner-gated external step + same-origin callback view on top of the existing #193 PKCE/state helpers (currently in the pairing stack ending at PR #209). Keep the callback strictly validation-only until the encrypted provisioning channel and Assist proof are also ready.

---

*This document is research only. No production pairing is enabled by its presence.*
