"""Isolated v3 restricted renewable Home Assistant TV login staging.

NOT registered with HA and not callable from a TV. Never issue the owner's
token or the default system-users role: both admin and normal users have
broad entity control. Owner-selected restricted identity only.
"""
from __future__ import annotations
from dataclasses import dataclass
from urllib.parse import urlsplit
from .credential_scope import check_restricted_tv_scope, TvScopeRejected, TvScopeProof
from .token_lease_journal import TokenLeaseJournal, TokenLease


class TvLoginDenied(ValueError):
    """Secret-free failure."""


@dataclass(frozen=True)
class IssuedTvCredential:
    """Sensitive in-memory handoff result. NEVER log or persist the tokens."""
    lease: TokenLease
    access_token: str
    refresh_token: str
    access_token_seconds: int


def _trusted_origin(origin):
    if type(origin) is not str or not origin.startswith("https://"):
        raise TvLoginDenied("Trusted HTTPS HA origin required")
    try:
        parsed = urlsplit(origin)
        if (not parsed.hostname or parsed.username or parsed.password
                or parsed.path not in ("", "/") or parsed.query
                or parsed.fragment or parsed.port == 0
                or any(c.isspace() for c in origin)):
            raise ValueError()
    except ValueError:
        raise TvLoginDenied("Invalid trusted HA origin") from None
    return origin.rstrip("/")


async def restricted_preflight(
    hass, *, owner_id, tv_user_id, tv_uuid, alert_entity_id,
    assist_entity_ids,
):
    """Fetch real live HA users, owner and complete HA inventory.

    All parameters must come from protected HA-owned setup, never TV input.
    Entity scope alone does NOT establish all Assist APIs enforce scope.
    """
    if (type(owner_id) is not str or not owner_id
            or type(tv_user_id) is not str or not tv_user_id
            or owner_id == tv_user_id):
        raise TvLoginDenied("Invalid identity")
    try:
        owner = await hass.auth.async_get_user(owner_id)
        user = await hass.auth.async_get_user(tv_user_id)
        if (owner is None or owner.id != owner_id
                or owner.is_owner is not True
                or owner.is_active is not True):
            raise TvLoginDenied("Owner approval unavailable")
        proof = check_restricted_tv_scope(
            user=user, owner_id=owner_id, tv_uuid=tv_uuid,
            alert_entity_id=alert_entity_id,
            assist_entity_ids=tuple(assist_entity_ids),
            current_entity_ids=tuple(hass.states.async_entity_ids()),
        )
    except (TvScopeRejected, TvLoginDenied):
        raise TvLoginDenied("Dedicated restricted TV user required") from None
    except Exception:
        raise TvLoginDenied("Unable to validate HA TV user") from None
    return user, proof


async def issue_provisional(
    hass, journal: TokenLeaseJournal, *, trusted_ha_origin,
    owner_id, tv_user_id, tv_uuid, alert_entity_id, assist_entity_ids,
):
    """Mint only after durable intent and strict scope recheck.

    No caller is allowed to commit or transfer a credential without the
    later reviewed authenticated TV handoff and positive capability tests.
    Incomplete intents are revoked on restart, never treated as paired.
    """
    if type(journal) is not TokenLeaseJournal:
        raise TvLoginDenied("Durable TV token journal required")
    origin = _trusted_origin(trusted_ha_origin)
    await journal.load()
    user, proof = await restricted_preflight(
        hass, owner_id=owner_id, tv_user_id=tv_user_id,
        tv_uuid=tv_uuid, alert_entity_id=alert_entity_id,
        assist_entity_ids=assist_entity_ids,
    )
    refresh = None
    try:
        lease = await journal.begin_intent(
            origin=origin, tv_uuid=tv_uuid,
            tv_user_id=tv_user_id, owner_id=owner_id,
        )
        user2, scope2 = await restricted_preflight(
            hass, owner_id=owner_id, tv_user_id=tv_user_id,
            tv_uuid=tv_uuid, alert_entity_id=alert_entity_id,
            assist_entity_ids=assist_entity_ids,
        )
        if user2 is not user or scope2 != proof:
            raise TvLoginDenied("TV scope changed during authorization")
        refresh = await hass.auth.async_create_refresh_token(
            user, client_id=lease.client_id,
        )
        if (refresh is None or refresh.user is not user
                or refresh.client_id != lease.client_id
                or refresh.token_type != "normal"
                or type(refresh.id) is not str or not refresh.id
                or type(refresh.token) is not str or not refresh.token):
            raise TvLoginDenied("HA issued an unexpected TV token")
        await journal.record_id(nonce=lease.nonce, token_id=refresh.id)
        access = hass.auth.async_create_access_token(refresh)
        lifetime = int(refresh.access_token_expiration.total_seconds())
        if type(access) is not str or not access or not 30 <= lifetime <= 86400:
            raise TvLoginDenied("Invalid HA access token")
        return IssuedTvCredential(
            lease, access, refresh.token, lifetime,
        )
    except Exception:
        # Never erase durable intent after a failure. Restart recovery
        # searches by the unique client ID even if record_id did not persist.
        if refresh is not None and getattr(refresh, "user", None) is user:
            try:
                hass.auth.async_remove_refresh_token(refresh)
            except Exception:
                pass
        raise TvLoginDenied("TV scoped token issuance failed") from None


def revoke_by_saved_lease(hass, lease: TokenLease) -> bool:
    """Only a server-loaded exact token handle may revoke. No TV token IDs."""
    if type(lease) is not TokenLease:
        raise TvLoginDenied("Invalid saved lease")
    lease.verify()
    if lease.token_id is None:
        raise TvLoginDenied("Incomplete token lease")
    token = hass.auth.async_get_refresh_token(lease.token_id)
    if token is None:
        return False
    if (token.id != lease.token_id or token.client_id != lease.client_id
            or token.user.id != lease.tv_user_id
            or token.user.id == lease.owner_id):
        raise TvLoginDenied("TV token identity mismatched")
    hass.auth.async_remove_refresh_token(token)
    return True
