"""Owner-opted full Home Assistant Users account for dedicated TV beta login.

A standard non-admin HA User can still read/control ALL entities. This path
MUST be used only after an active HA owner explicitly checks the broad-access
consent in the authenticated owner panel; the caller has confirmed TV remote
and a v3 encrypted approval receipt. Never grant or reuse the owner's token.
"""
from __future__ import annotations
from homeassistant.auth.const import GROUP_ID_USER
from .scoped_login_issuer import IssuedTvCredential, _trusted_origin
from .token_lease_journal import TokenLeaseJournal


class DedicatedTvLoginDenied(ValueError):
    pass


async def provision_dedicated_tv_login(
    hass, journal: TokenLeaseJournal, *, owner_id: str, tv_uuid: str,
    ha_origin: str, remote_login: bool = False,
):
    """Provision a dedicated non-admin user and provisional renewable token.

    Remote-capable only if HA's own HTTPS URL resolver selected an external
    or Cloud endpoint, never from an untrusted TV address.

    Caller must already have the exact server-derived owner opt-in and v3
    physical TV proof. No public endpoint calls this function directly.
    """
    if type(journal) is not TokenLeaseJournal:
        raise DedicatedTvLoginDenied("TV credential journal unavailable")
    origin = _trusted_origin(ha_origin)
    if type(remote_login) is not bool:
        raise DedicatedTvLoginDenied("Invalid dedicated TV network scope")
    local_only = not remote_login
    owner = await hass.auth.async_get_user(owner_id)
    if (owner is None or owner.id != owner_id
            or owner.is_active is not True or owner.is_owner is not True):
        raise DedicatedTvLoginDenied("Authenticated owner unavailable")
    await journal.load()
    # Separate HA user, not owner. Owner was explicitly warned that
    # the *standard* Users role can still read/control all entities.
    user = await hass.auth.async_create_user(
        "Kellie's Voice TV " + tv_uuid[-8:],
        group_ids=[GROUP_ID_USER], local_only=local_only,
    )
    if (user is None or user.is_owner or user.is_admin or
            user.system_generated or user.local_only is not local_only
            or user.is_active is not True or user.id == owner_id
            or {group.id for group in user.groups} != {GROUP_ID_USER}):
        raise DedicatedTvLoginDenied("Dedicated TV account rejected")
    refresh = None
    try:
        lease = await journal.begin_intent(
            origin=origin, tv_uuid=tv_uuid,
            tv_user_id=user.id, owner_id=owner_id)
        refresh = await hass.auth.async_create_refresh_token(
            user, client_id=lease.client_id)
        if (refresh is None or refresh.user is not user
                or refresh.client_id != lease.client_id
                or refresh.token_type != "normal"
                or type(refresh.token) is not str or not refresh.token
                or type(refresh.id) is not str or not refresh.id):
            raise DedicatedTvLoginDenied("Unexpected HA token identity")
        recorded = await journal.record_id(
            nonce=lease.nonce, token_id=refresh.id)
        access = hass.auth.async_create_access_token(refresh)
        lifetime = int(refresh.access_token_expiration.total_seconds())
        if (type(access) is not str or not access or
                not 30 <= lifetime <= 86400):
            raise DedicatedTvLoginDenied("HA access token invalid")
        return (IssuedTvCredential(
            lease, access, refresh.token, lifetime), recorded, user)
    except Exception:
        if refresh is not None and refresh.user is user:
            try:
                hass.auth.async_remove_refresh_token(refresh)
            except Exception:
                pass  # Retain durable intent for restart recovery.
        raise DedicatedTvLoginDenied("Dedicated TV login failed") from None
