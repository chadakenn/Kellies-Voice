"""#214 H4: durable, no-secret intent journal and restart token reaper.

V3 isolated QA: not registered for credential issuance yet. No credentials are minted/transferred.
A separate, independently reviewed caller must authorize a non-admin TV user,
then await begin_intent() BEFORE generating ANY refresh token. The unique
client ID permits lookup if HA crashes after issuance but before record_id().
"""
from __future__ import annotations

import asyncio
from dataclasses import dataclass, asdict, replace
import json
import os
from pathlib import Path
import re
import secrets
import tempfile
from typing import Any
from urllib.parse import urlsplit
from uuid import UUID

_VERSION = 1
_MAX_LEASES = 100
_NONCE = re.compile(r"[0-9a-f]{32}\Z")
_VALID_STATES = frozenset(("intent", "issued", "revoking", "revoked", "committed"))
_TOKEN_ID = re.compile(r"[A-Za-z0-9_-]{1,128}\Z")


class LeaseRecoveryError(RuntimeError):
    """Opaque. No auth secrets or user records in errors."""


@dataclass(frozen=True)
class TokenLease:
    tv_uuid: str
    tv_user_id: str
    owner_id: str
    client_id: str
    nonce: str
    state: str
    token_id: str | None = None

    def verify(self) -> None:
        try:
            tv_uuid = str(UUID(self.tv_uuid))
        except (ValueError, TypeError, AttributeError):
            raise LeaseRecoveryError("Pairing record invalid") from None
        if (tv_uuid != self.tv_uuid or UUID(tv_uuid).version != 4
                or UUID(tv_uuid).variant != "specified in RFC 4122"
                or type(self.nonce) is not str or not _NONCE.fullmatch(self.nonce)
                or type(self.client_id) is not str
                or not self.client_id.endswith(
                    "/kellies-voice/tv/" + self.tv_uuid + "/attempt/" + self.nonce)
                or urlsplit(self.client_id).scheme not in ("http", "https")
                or type(self.owner_id) is not str or not self.owner_id
                or type(self.tv_user_id) is not str or not self.tv_user_id
                or self.owner_id == self.tv_user_id
                or self.state not in _VALID_STATES
                or (self.token_id is not None and (type(self.token_id) is not str
                    or not _TOKEN_ID.fullmatch(self.token_id)))
                or (self.state in ("issued", "committed") and not self.token_id)):
            raise LeaseRecoveryError("Pairing record invalid")


def _read_file(path: Path) -> dict:
    try:
        raw = path.read_bytes()
    except FileNotFoundError:
        return {"version": _VERSION, "leases": []}
    if len(raw) > 512_000:
        raise LeaseRecoveryError("Pairing journal too large")
    try:
        doc = json.loads(raw)
    except (UnicodeError, ValueError):
        raise LeaseRecoveryError("Pairing journal corrupt") from None
    if (type(doc) is not dict or doc.get("version") != _VERSION
            or type(doc.get("leases")) is not list
            or len(doc["leases"]) > _MAX_LEASES):
        raise LeaseRecoveryError("Pairing journal unsupported")
    return doc


def _write_atomic(path: Path, doc: dict) -> None:
    """Synchronous private journal write with atomic replace and fsync."""
    # Never use a scheduled/delayed HA Store save for a pre-issuance guard.
    # A returned Store.async_save can swallow filesystem write errors.
    payload = json.dumps(doc, separators=(",", ":"), sort_keys=True).encode()
    if len(payload) > 512_000:
        raise LeaseRecoveryError("Pairing journal too large")
    temp_name = None
    try:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        fd, temp_name = tempfile.mkstemp(prefix=".kellies-lease-", dir=path.parent)
        with os.fdopen(fd, "wb") as handle:
            os.fchmod(handle.fileno(), 0o600)
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_name, path)
        temp_name = None
        # Persist the rename itself, including abrupt power loss on POSIX.
        if os.name == "posix":
            fd = os.open(path.parent, os.O_RDONLY)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)
    except OSError:
        raise LeaseRecoveryError("Pairing journal could not persist") from None
    finally:
        if temp_name:
            try:
                os.unlink(temp_name)
            except FileNotFoundError:
                pass


class TokenLeaseJournal:
    """Serializes a durable intent BEFORE token issuance.

    No HA AuthManager access occurs in begin_intent; it merely returns a
    unique client ID. Caller MUST await it before requesting a token.
    """

    def __init__(self, hass: Any, path: Path):
        self._hass = hass
        self._path = Path(path)
        self._lock = asyncio.Lock()
        self._leases: dict[str, TokenLease] | None = None

    async def load(self) -> None:
        async with self._lock:
            if self._leases is not None:
                return
            doc = await self._hass.async_add_executor_job(_read_file, self._path)
            entries = {}
            for item in doc["leases"]:
                try:
                    if type(item) is not dict:
                        raise ValueError("bad record")
                    lease = TokenLease(**item)
                    lease.verify()
                except (ValueError, TypeError, LeaseRecoveryError):
                    raise LeaseRecoveryError("Pairing journal corrupt") from None
                if lease.nonce in entries:
                    raise LeaseRecoveryError("Pairing journal duplicates")
                entries[lease.nonce] = lease
            self._leases = entries

    async def _persist(self, next_leases: dict[str, TokenLease]) -> None:
        if len(next_leases) > _MAX_LEASES:
            raise LeaseRecoveryError("Pairing journal capacity exceeded")
        await self._hass.async_add_executor_job(
            _write_atomic, self._path, {
                "version": _VERSION,
                "leases": [asdict(x) for x in next_leases.values()],
            }
        )
        # Publish to memory only after the replace has definitely succeeded.
        self._leases = next_leases

    async def begin_intent(self, *, origin: str, tv_uuid: str,
                           tv_user_id: str, owner_id: str) -> TokenLease:
        """No token creation before a durable, unique attempt ID exists."""
        await self.load()
        if type(origin) is not str:
            raise LeaseRecoveryError("HA origin invalid")
        origin = origin.rstrip("/")
        parts = urlsplit(origin)
        if (parts.scheme not in ("http", "https")
                or not parts.netloc or not parts.hostname
                or parts.path or parts.username or parts.password
                or parts.query or parts.fragment or any(ch.isspace() for ch in origin)):
            raise LeaseRecoveryError("HA origin invalid")
        nonce = secrets.token_hex(16)
        lease = TokenLease(
            tv_uuid=tv_uuid, tv_user_id=tv_user_id, owner_id=owner_id,
            client_id=origin + "/kellies-voice/tv/" + tv_uuid + "/attempt/" + nonce,
            nonce=nonce, state="intent",
        )
        lease.verify()
        async with self._lock:
            assert self._leases is not None
            if any(x.tv_uuid == tv_uuid and x.state not in ("revoked",)
                   for x in self._leases.values()):
                raise LeaseRecoveryError("Existing TV lease must be resolved")
            records = dict(self._leases)
            records[nonce] = lease
            await self._persist(records)
        return lease

    async def record_id(self, *, nonce: str, token_id: str) -> TokenLease:
        """Only after obtaining and verifying the same HA token's identity."""
        await self.load()
        async with self._lock:
            assert self._leases is not None
            old = self._leases.get(nonce)
            if old is None or old.state != "intent":
                raise LeaseRecoveryError("Pairing issuance state invalid")
            new = replace(old, state="issued", token_id=token_id)
            new.verify()
            records = dict(self._leases)
            records[nonce] = new
            await self._persist(records)
            return new

    async def commit(self, nonce: str) -> None:
        """Only after full TV Assist + alert permission proof and ack."""
        await self.load()
        async with self._lock:
            assert self._leases is not None
            old = self._leases.get(nonce)
            if old is None or old.state != "issued":
                raise LeaseRecoveryError("Pairing commit not allowed")
            records = dict(self._leases)
            records[nonce] = replace(old, state="committed")
            await self._persist(records)

    async def recover_unfinished(self) -> dict[str, str]:
        """Revoke orphan per-attempt tokens and retain failures for next boot.

        Must run ONLY on startup before any pairing flow is activated, never
        while a current issuance is in flight. Uses HA's own authenticated
        user.refresh_tokens records. Does NOT read refresh secrets.
        """
        await self.load()
        result: dict[str, str] = {}
        async with self._lock:
            assert self._leases is not None
            for nonce, record in list(self._leases.items()):
                if record.state in ("committed", "revoked"):
                    continue
                try:
                    person = await self._hass.auth.async_get_user(record.tv_user_id)
                    if (person is None or getattr(person, "id", None)
                            != record.tv_user_id):
                        raise LeaseRecoveryError("TV user unavailable")
                    candidates = [
                        rt for rt in person.refresh_tokens.values()
                        if getattr(rt, "client_id", None) == record.client_id
                        and getattr(getattr(rt, "user", None), "id", None)
                        == record.tv_user_id
                    ]
                    # An exact client ID with a DIFFERENT stored token ID
                    # is not proof no token exists. Never clear its intent.
                    if (record.token_id is not None and any(
                            rt.id != record.token_id for rt in candidates)):
                        raise LeaseRecoveryError("TV token identity mismatch")
                    matches = candidates
                    if len(matches) > 1:
                        raise LeaseRecoveryError("Ambiguous TV token identity")
                    if matches:
                        # Durable 'revoking' state is written before HA mutate.
                        # If the process dies here, next boot retries safely.
                        if record.state != "revoking":
                            current = dict(self._leases)
                            current[nonce] = replace(record, state="revoking")
                            await self._persist(current)
                        self._hass.auth.async_remove_refresh_token(matches[0])
                    current = dict(self._leases)
                    current[nonce] = replace(current[nonce], state="revoked")
                    await self._persist(current)
                    result[nonce] = "revoked"
                except Exception:
                    # Do not delete intent or release any possibly orphan token
                    # on a failed read/validation/disk write/revoke.
                    result[nonce] = "retry_required"
        return result

    def snapshot(self) -> tuple[TokenLease, ...]:
        if self._leases is None:
            raise LeaseRecoveryError("Load journal first")
        return tuple(self._leases.values())
