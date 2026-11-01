"""Fail-closed TV account permission preflight for future credential issuance.

NO authentication, credential creation, token lookup, or HA user mutation.
Only accepts an already-selected *dedicated* account from the authenticated
owner's server-side setup flow. Never trust a TV-supplied user ID.
Passing this check does NOT imply every HA API enforces entity permissions.
"""
from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Iterable
from uuid import UUID

_ENTITY_ID = re.compile(r"[a-z][a-z0-9_]*\.[a-z0-9_]+\Z")
_BLOCKED_GROUPS = frozenset(
    ("system-admin", "system-users", "system-read-only")
)
_OPERATIONS = frozenset(("read", "control", "edit"))
_MAX_ENTITIES = 128


class TvScopeRejected(ValueError):
    """Opaque failure: no account identifiers or device information in text."""


@dataclass(frozen=True)
class TvScopeProof:
    """Server-only, NON-authorizing result, never a token or permission grant."""

    tv_uuid: str
    tv_user_id: str
    alert_entity_id: str
    assist_entity_ids: tuple[str, ...]


def _id(value: object) -> str:
    if type(value) is not str or not _ENTITY_ID.fullmatch(value):
        raise TvScopeRejected("Invalid entity scope")
    return value


def _groups_restrict_to_exact_entities(groups, allowed: set[str]) -> None:
    """Block wildcard groups, inherited default roles and future-entity grants."""
    if not isinstance(groups, (list, tuple)) or not groups:
        raise TvScopeRejected("Dedicated restricted group required")
    for group in groups:
        group_id = getattr(group, "id", None)
        policy = getattr(group, "policy", None)
        if (type(group_id) is not str or group_id in _BLOCKED_GROUPS
                or type(policy) is not dict
                or set(policy) != {"entities"}):
            raise TvScopeRejected("Broad TV account group denied")
        entity_policy = policy.get("entities")
        if (type(entity_policy) is not dict
                or set(entity_policy) != {"entity_ids"}):
            # Domains, areas, devices, all, or whole-entity wildcards may
            # confer access to newly created entities. Refuse all.
            raise TvScopeRejected("Non-explicit TV account policy denied")
        entries = entity_policy["entity_ids"]
        if type(entries) is not dict or not entries:
            raise TvScopeRejected("Empty TV account policy")
        for raw_entity, operations in entries.items():
            entity = _id(raw_entity)
            if entity not in allowed or type(operations) is not dict:
                raise TvScopeRejected("Unexpected entity grant")
            if not operations or not set(operations) <= _OPERATIONS:
                raise TvScopeRejected("Unknown entity permission")
            for action, grant in operations.items():
                if grant is not True and grant is not None:
                    raise TvScopeRejected("Unknown permission value")
                if action == "edit" and grant is True:
                    raise TvScopeRejected("TV may not edit entity configuration")


def check_restricted_tv_scope(
    *, user, owner_id: str, tv_uuid: str, alert_entity_id: str,
    assist_entity_ids: Iterable[str], current_entity_ids: Iterable[str],
) -> TvScopeProof:
    """Check candidate user group policy AND effective HA permissions.

    Scope covers exactly one read-only TV alert-position entity and explicit
    Assist read/control entities. All current HA entities must be enumerated
    for a guard against accidental extra access. This is a *preflight*, not
    proof that Assist itself or every HA WebSocket command is scoped.
    """
    try:
        canonical = str(UUID(tv_uuid))
        valid_uuid = UUID(tv_uuid).version == 4 and (
            UUID(tv_uuid).variant == "specified in RFC 4122")
    except (TypeError, ValueError, AttributeError):
        valid_uuid = False
        canonical = ""
    if not valid_uuid or canonical != tv_uuid:
        raise TvScopeRejected("Invalid TV identity")
    tv_user_id = getattr(user, "id", None)
    if (type(owner_id) is not str or not owner_id
            or type(tv_user_id) is not str or not tv_user_id
            or tv_user_id == owner_id
            or getattr(user, "is_owner", True) is not False
            or getattr(user, "is_admin", True) is not False
            or getattr(user, "is_active", False) is not True
            or getattr(user, "system_generated", True) is not False
            or getattr(user, "local_only", False) is not True):
        raise TvScopeRejected("Dedicated local non-admin TV user required")

    alert_id = _id(alert_entity_id)
    if not alert_id.startswith("select."):
        raise TvScopeRejected("TV alert entity invalid")
    try:
        assist_ids = tuple(assist_entity_ids)
        actual_ids = tuple(current_entity_ids)
    except (TypeError, ValueError):
        raise TvScopeRejected("Invalid entity inventory") from None
    if (not assist_ids or len(assist_ids) > _MAX_ENTITIES
            or len(assist_ids) != len(set(assist_ids))
            or len(actual_ids) != len(set(actual_ids))
            or alert_id in assist_ids):
        raise TvScopeRejected("Invalid explicit TV scope")
    for entity in assist_ids:
        _id(entity)
    for entity in actual_ids:
        _id(entity)
    selected = {alert_id, *assist_ids}
    if (len(selected) > _MAX_ENTITIES
            or not selected.issubset(set(actual_ids))):
        raise TvScopeRejected("Required TV entities unavailable")
    _groups_restrict_to_exact_entities(getattr(user, "groups", None), selected)

    permissions = getattr(user, "permissions", None)
    if permissions is None:
        raise TvScopeRejected("TV permissions unavailable")
    try:
        if any(permissions.access_all_entities(op) for op in _OPERATIONS):
            raise TvScopeRejected("Wildcard TV access denied")
        for entity in actual_ids:
            granted = {
                op for op in _OPERATIONS
                if permissions.check_entity(entity, op)
            }
            if entity == alert_id:
                if granted != {"read"}:
                    raise TvScopeRejected("TV alert permission not scoped")
            elif entity in selected:
                if granted != {"read", "control"}:
                    raise TvScopeRejected("Assist entity permission not scoped")
            elif granted:
                raise TvScopeRejected("Unrelated HA entity permission denied")
    except TvScopeRejected:
        raise
    except Exception:
        raise TvScopeRejected("Unable to verify TV account permissions") from None

    return TvScopeProof(
        tv_uuid=canonical, tv_user_id=tv_user_id,
        alert_entity_id=alert_id, assist_entity_ids=tuple(sorted(assist_ids)),
    )
