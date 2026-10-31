"""Owner-opted full Home Assistant Users account for dedicated TV beta login.

A standard non-admin HA User can still read/control ALL entities. This path
MUST be used only after an active HA owner explicitly checks the broad-access
consent in the authenticated owner panel; the caller has confirmed TV remote
and a v3 encrypted approval receipt. Never grant or reuse the owner's token.
"""
from __future__ import annotations
import logging
from uuid import UUID
from homeassistant.auth.const import GROUP_ID_USER
from .scoped_login_issuer import IssuedTvCredential, _trusted_origin
from .token_lease_journal import TokenLeaseJournal


_LOGGER = logging.getLogger(__name__)


class DedicatedTvLoginDenied(ValueError):
    pass


def _valid_tv_uuid(value: object) -> bool:
    """No HA user creation for a malformed or noncanonical TV identity."""
    if type(value) is not str:
        return False
    try:
        uid = UUID(value)
        return str(uid) == value and uid.version == 4 and uid.variant == "specified in RFC 4122"
    except (ValueError, AttributeError):
        return False


async def _remove_failed_tv_user(hass, user: object, owner_id: str) -> None:
    """Best-effort cleanup of only the user created by this attempt.

    Never delete an owner, admin, system user or preexisting account.
    If removal fails, try deactivation to invalidate outstanding tokens.
    """
    if user is None:
        return
    if (type(getattr(user, "id", None)) is not str
            or not user.id or user.id == owner_id
            or getattr(user, "is_owner", None) is not False
            or getattr(user, "is_admin", None) is not False
            or getattr(user, "system_generated", None) is not False):
        _LOGGER.warning("Dedicated TV cleanup blocked by account identity guard")
        return
    try:
        await hass.auth.async_remove_user(user)
        return
    except Exception:
        _LOGGER.warning("Dedicated TV cleanup could not remove new account")
    try:
        await hass.auth.async_deactivate_user(user)
    except Exception:
        _LOGGER.warning("Dedicated TV cleanup could not deactivate new account")


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
    if not _valid_tv_uuid(tv_uuid):
        raise DedicatedTvLoginDenied("Invalid TV identity")
    origin = _trusted_origin(ha_origin)
    if type(remote_login) is not bool:
        raise DedicatedTvLoginDenied("Invalid dedicated TV network scope")
    local_only = not remote_login
    owner = await hass.auth.async_get_user(owner_id)
    if (owner is None or owner.id != owner_id
            or owner.is_active is not True or owner.is_owner is not True):
        raise DedicatedTvLoginDenied("Authenticated owner unavailable")
    await journal.load()
    # Do not create an orphan HA account when a previous enrollment
    # already owns this TV identity. A committed login must never be
    # overwritten automatically. Unfinished leases require the audited
    # restart/revocation recovery path, not a silent retry.
    if any(lease.tv_uuid == tv_uuid and lease.state != "revoked"
           for lease in journal.snapshot()):
        _LOGGER.warning("Dedicated TV enrollment blocked at phase existing_tv_lease")
        raise DedicatedTvLoginDenied("Existing TV enrollment must be resolved")
    # Creation, lease registration and issuance share a failure cleanup
    # boundary. Otherwise a failed durable lease write or HA token API call
    # can leave behind a broad-access system-users TV account.
    user = None
    refresh = None
    phase = "user_create"
    try:
        # Separate HA user, not owner. Owner explicitly opted into the broad
        # standard Users role; this is NOT a least-privilege scoped grant.
        user = await hass.auth.async_create_user(
            "Kellie's Voice TV " + tv_uuid[-8:],
            group_ids=[GROUP_ID_USER], local_only=local_only,
        )
        phase = "user_validation"
        if (user is None or user.is_owner or user.is_admin or
                user.system_generated or user.local_only is not local_only
                or user.is_active is not True or user.id == owner_id
                or {group.id for group in user.groups} != {GROUP_ID_USER}):
            raise DedicatedTvLoginDenied("Dedicated TV account rejected")
        phase = "lease_intent"
        lease = await journal.begin_intent(
            origin=origin, tv_uuid=tv_uuid,
            tv_user_id=user.id, owner_id=owner_id)
        phase = "refresh_issuance"
        refresh = await hass.auth.async_create_refresh_token(
            user, client_id=lease.client_id)
        if (refresh is None or refresh.user is not user
                or refresh.client_id != lease.client_id
                or refresh.token_type != "normal"
                or type(refresh.token) is not str or not refresh.token
                or type(refresh.id) is not str or not refresh.id):
            raise DedicatedTvLoginDenied("Unexpected HA token identity")
        phase = "record_token_handle"
        recorded = await journal.record_id(
            nonce=lease.nonce, token_id=refresh.id)
        phase = "access_issuance"
        access = hass.auth.async_create_access_token(refresh)
        lifetime = int(refresh.access_token_expiration.total_seconds())
        if (type(access) is not str or not access or
                not 30 <= lifetime <= 86400):
            raise DedicatedTvLoginDenied("HA access token invalid")
        phase = "credential_ready"
        return (IssuedTvCredential(
            lease, access, refresh.token, lifetime), recorded, user)
    except Exception as error:
        # Intentionally log ONLY a fixed phase and exception *type*.
        # Never log exception text, TV identifiers, account info, URLs,
        # token IDs or token material (some HA errors can include them).
        _LOGGER.warning("Dedicated TV login failed at phase %s (%s)",
                        phase, type(error).__name__)
        if refresh is not None and getattr(refresh, "user", None) is user:
            try:
                hass.auth.async_remove_refresh_token(refresh)
            except Exception:
                pass  # Retain durable intent for restart recovery.
        # HA's supported AuthManager async_remove_user also revokes outstanding
        # refresh tokens. Preserve the durable intent for restart cleanup.
        await _remove_failed_tv_user(hass, user, owner_id)
        raise DedicatedTvLoginDenied("Dedicated TV login failed") from None
