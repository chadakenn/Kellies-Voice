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
    url=authorize_url,           # must include flow_id (or state that carries it)
)
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

- Client ID is an IndieAuth URL (typically the HA origin itself or a documented metadata URL).
- Redirect URI must be same-origin (or declared via HTML link tag / OAuth client metadata document at the client_id URL).
- PKCE S256 is required for native / non-confidential clients.
- Owner must be authenticated and explicitly consent. Never silent.

### 3. Callback (`HomeAssistantView`)

The integration registers a view (or uses a known path) that receives the redirect with `code` and `state`.

Typical pattern:

```python
class OAuthCallbackView(HomeAssistantView):
    url = "/api/kellies_voice/oauth/callback"   # example
    name = "api:kellies_voice:oauth:callback"
    requires_auth = False                       # public callback; security via state + PKCE

    async def get(self, request):
        flow_id = request.query.get("state")
        code = request.query.get("code")
        # Validate state matches an active flow, then:
        await hass.config_entries.flow.async_configure(
            flow_id,
            user_input={"code": code, "state": flow_id},
        )
        return web.Response(text="<script>window.close()</script>")
```

### 4. Resume the flow

```python
async def async_step_authorize(self, user_input=None):
    if user_input is None:
        # first call – show external step
        return self.async_external_step(step_id="authorize", url=...)

    # second call – data arrived from callback
    # validate code / state / PKCE, then:
    return self.async_external_step_done(next_step_id="exchange")
```

Work with the code happens in the *next* step, not inside the callback view.

### 5. Local HTTP / HTTPS / reverse-proxy considerations

- Client ID and metadata documents must be HTTPS when using the metadata document form.
- Same-origin redirect URIs work with both local HTTP (`http://homeassistant.local:8123`) and HTTPS.
- Reverse proxies must preserve query parameters and the original host (or correctly set `X-Forwarded-*` / `HA-Frontend-Base`).
- Loopback HTTP is acceptable for local HA; public LAN plaintext HTTP is not for token endpoints.
- User identity is preserved because the owner is already authenticated in the HA frontend session when they approve the authorize request.

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
