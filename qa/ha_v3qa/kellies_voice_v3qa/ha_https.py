"""Choose HA's secure network URL, including an active Nabu Casa Cloud URL.

HA's config.external_url is unset when Settings > Network uses Cloud.
The supported HA get_url() resolver handles its actual HTTPS availability.
The selected origin and remote-capable flag are derived SERVER-SIDE, not TV
discovery, query parameters or the approval panel.
"""
from __future__ import annotations

from .scoped_login_issuer import _trusted_origin


def resolve_tv_login_origin(hass) -> tuple[str, bool]:
    """Return (trusted HTTPS origin, needs remote-capable dedicated account).

    Prefer active HA Cloud; then manually configured external HTTPS; then
    internal HTTPS. Plaintext local HTTP is NOT a credential transport.
    """
    from homeassistant.helpers.network import get_url, NoURLAvailableError

    # Never silently upgrade an arbitrary mDNS-provided host or TV address.
    origin = _trusted_origin(get_url(
        hass,
        require_ssl=True,
        prefer_external=True,
        prefer_cloud=True,
        allow_cloud=True,
        allow_external=True,
        allow_internal=True,
    ))

    try:
        external_origin = _trusted_origin(get_url(
            hass,
            require_ssl=True,
            prefer_external=True,
            prefer_cloud=True,
            allow_cloud=True,
            allow_external=True,
            allow_internal=False,
        ))
    except (NoURLAvailableError, ValueError):
        external_origin = None

    # HA rejects local_only users via Cloud/remote /auth/token and WebSocket.
    # The owner panel explicitly warns of the broad, remotely reachable role.
    return origin, origin == external_origin
