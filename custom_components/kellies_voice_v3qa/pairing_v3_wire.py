"""KVPair/v3 HA-to-TV ordered public-key handshake, NO credentials.

A future HA config flow may invoke the transport ONLY after the owner selects
the discovered TV. This module is not registered with the existing v2
config flow, alert WebSockets, OAuth endpoints or token issuer.

The four v3 routes are separate from all v1/v2 routes. A response with a
different protocol number or extra/unknown fields always aborts, never
downgrades. All network material is public P-256 keys, commitments or a
proof of a *physical* TV-remote OK. Nothing here authorizes a user.
"""
from __future__ import annotations

import base64
import hmac
import ipaddress
import json
import re
import time
from dataclasses import dataclass

from .pairing_key_commitment import CommitmentSession, _session_bytes
from .pairing_v3_verified import (
    comparison_code, physical_tv_ok_proof, public_der,
)
from .validation import clean_tv_device_id

_B64 = re.compile(r"[A-Za-z0-9_-]+\Z")
_MAX_BODY = 1024


def _encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _decode(value: object, length: int | None = None) -> bytes:
    if type(value) is not str or not value or _B64.fullmatch(value) is None:
        raise ValueError("Invalid v3 Base64URL field")
    try:
        raw = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
    except (ValueError, base64.binascii.Error):
        raise ValueError("Invalid v3 Base64URL field") from None
    if _encode(raw) != value or (length is not None and len(raw) != length):
        raise ValueError("Noncanonical or incorrect v3 field size")
    return raw


def _response(doc: object, expected: set[str], session: str) -> dict:
    if type(doc) is not dict or set(doc) != expected:
        raise ValueError("Unexpected v3 response structure")
    if type(doc["protocol"]) is not int or doc["protocol"] != 3:
        raise ValueError("V3 downgrade or wrong version")
    if doc["session"] != session:
        raise ValueError("V3 pairing session changed")
    return doc


@dataclass(frozen=True)
class VerifiedTv:
    tv_uuid: str
    session: str


class HaV3WireAttempt:
    """One-shot HA initiator. Transport implements post(path, obj) and
    get(path, params), returning decoded strict JSON objects.
    """

    def __init__(self, session: str, *, private=None, nonce=None,
                 retain_owner_receipt: bool = False):
        _session_bytes(session)
        from cryptography.hazmat.primitives.asymmetric import ec
        self._private = private or ec.generate_private_key(ec.SECP256R1())
        self.session = session
        self._barrier = CommitmentSession(
            "ha", session, public_der(self._private), nonce=nonce)
        self._expires_at = time.monotonic() + 120
        self._used = False
        self._confirmed = False
        self._retain_owner_receipt = retain_owner_receipt is True
        self._verified_tv: VerifiedTv | None = None
        self._receipt_used = False
        self.code: str | None = None

    async def start(self, transport) -> str:
        if self._used or time.monotonic() >= self._expires_at:
            raise ValueError("V3 attempt already started or expired")
        self._used = True
        try:
            sid = self.session
            response = _response(
                await transport.post("/v3/pair/commit", {
                    "protocol": 3, "session": sid,
                    "ha_commit": _encode(self._barrier.public_commit),
                }), {"protocol", "session", "tv_commit"}, sid,
            )
            self._barrier.receive_peer_commit(
                _decode(response["tv_commit"], length=32))
            ha_key, ha_nonce = self._barrier.reveal()
            acknowledged = _response(
                await transport.post("/v3/pair/reveal-ha", {
                    "protocol": 3, "session": sid,
                    "ha_key": _encode(ha_key), "ha_nonce": _encode(ha_nonce),
                }), {"protocol", "session", "ha_verified"}, sid,
            )
            if acknowledged["ha_verified"] is not True:
                raise ValueError("TV did not verify HA commitment")
            response = _response(
                await transport.get("/v3/pair/reveal-tv", {
                    "protocol": 3, "session": sid,
                }), {"protocol", "session", "tv_key", "tv_nonce"}, sid,
            )
            tv_key = _decode(response["tv_key"])
            tv_nonce = _decode(response["tv_nonce"], length=32)
            self._barrier.receive_peer_reveal(tv_key, tv_nonce)
            self.code = comparison_code(self._private, self._barrier)
            return self.code
        except Exception:
            self.close()
            raise

    async def confirmed_by_tv_remote(self, transport) -> VerifiedTv | None:
        """Query only after the owner has compared the v3 code on both UIs."""
        if (self._confirmed or not self._barrier.ready or self.code is None
                or time.monotonic() >= self._expires_at):
            self.close()
            raise ValueError("V3 physical confirmation not available")
        try:
            response = await transport.get("/v3/pair/status", {
                "protocol": 3, "session": self.session,
            })
            if type(response) is not dict:
                raise ValueError("Invalid TV approval response")
            if response.get("approved") is False:
                _response(response,
                          {"protocol", "session", "approved"}, self.session)
                return None
            _response(response,
                      {"protocol", "session", "approved", "proof", "tv_uuid"},
                      self.session)
            if response["approved"] is not True:
                raise ValueError("TV has not approved v3 pairing")
            tv_uuid = clean_tv_device_id(response["tv_uuid"])
            if tv_uuid != response["tv_uuid"]:
                raise ValueError("Noncanonical TV identity")
            expected = physical_tv_ok_proof(
                self._private, self._barrier, tv_uuid=tv_uuid)
            provided = response["proof"]
            if type(provided) is not str or not hmac.compare_digest(
                    expected, provided):
                raise ValueError("Invalid physical TV approval proof")
            self._confirmed = True
            result = VerifiedTv(tv_uuid=tv_uuid, session=self.session)
            if self._retain_owner_receipt:
                # Only for the isolated owner-approval hardware QA. Retain
                # bounded ECDH state until a real HA-owner claim is consumed.
                self._verified_tv = result
                self.code = None
            else:
                self.close()
            return result
        except Exception:
            self.close()
            raise

    async def finish_owner_receipt(self, transport, *, hass, claims, flow_id,
                                   retain_for_login=False):
        """Issue only after real owner consent; demand a v3 AEAD TV ack.

        The TV receives only 'owner-approved'. Never any HA credential.
        Every attempt, including failed network ACK, burns the key state.
        """
        from .owner_receipt import issue_after_owner_claim
        from .pairing_v3_qa_channel import V3QaPingChannel
        if (not self._retain_owner_receipt or self._receipt_used
                or not self._confirmed or self._verified_tv is None
                or time.monotonic() >= self._expires_at):
            self.close()
            raise ValueError("Owner receipt attempt unavailable")
        self._receipt_used = True
        tv = self._verified_tv
        try:
            # Instantiate acknowledgement verifier before releasing owner's
            # one-shot claim. Key stays in memory only while this call runs.
            channel = V3QaPingChannel(
                private_key=self._private, verified=self._barrier,
                tv_uuid=tv.tv_uuid, ttl_seconds=30.0)
            try:
                number, encrypted = await issue_after_owner_claim(
                    hass=hass, claims=claims, flow_id=flow_id,
                    verified_tv=tv, private_key=self._private,
                    verified=self._barrier)
                response = _response(await transport.post(
                    "/v3/pair/owner-receipt", {
                        "protocol": 3, "session": self.session,
                        "sequence": number, "receipt": _encode(encrypted),
                        "login_next": retain_for_login is True,
                    }), {"protocol", "session", "ack_seq", "ack"}, self.session)
                if type(response["ack_seq"]) is not int:
                    raise ValueError("Invalid TV owner receipt ack sequence")
                ack = channel.open(
                    response["ack_seq"], _decode(response["ack"], length=23))
                if ack != b"qa-pong":
                    raise ValueError("Unexpected owner receipt acknowledgement")
                return True
            finally:
                channel.close()
        finally:
            if not retain_for_login or not self._confirmed:
                self.close()

    async def finish_renewable_login(self, transport, *,
                                    hass, journal, owner_id, ha_origin,
                                    remote_login=False):
        """After signed receipt ACK, transfer one renewable dedicated-TV grant.

        Only the authenticated HA owner consent may supply owner_id.
        No owner, no preexisting TV receipt, no account creation.
        """
        from .paired_tv_login import provision_dedicated_tv_login
        from .scoped_token_handoff import HaTokenHandoff
        if (not self._receipt_used or not self._confirmed
                or self._verified_tv is None
                or time.monotonic() >= self._expires_at
                or type(owner_id) is not str or not owner_id):
            self.close()
            raise ValueError("TV owner consent missing")
        tv = self._verified_tv
        user = None
        committed = False
        try:
            issued, recorded, user = await provision_dedicated_tv_login(
                hass, journal, owner_id=owner_id, tv_uuid=tv.tv_uuid,
                ha_origin=ha_origin, remote_login=remote_login)
            handoff = HaTokenHandoff(
                private_key=self._private, verified=self._barrier, tv=tv,
                ttl_seconds=30)
            try:
                seq, packet = handoff.seal_recorded_provisional(
                    issued, recorded)
                response = _response(await transport.post(
                    "/v3/pair/token-stage", {
                        "protocol": 3, "session": self.session,
                        "sequence": seq, "encrypted": _encode(packet),
                        "ha_origin": ha_origin,
                    }), {"protocol", "session", "ack_seq", "ack"},
                    self.session)
                handoff.accept_stage_ack(
                    response["ack_seq"], _decode(response["ack"]))
                seq, ready = handoff.seal_commit_ready()
                response = _response(await transport.post(
                    "/v3/pair/token-commit", {
                        "protocol": 3, "session": self.session,
                        "sequence": seq, "encrypted": _encode(ready),
                    }), {"protocol", "session", "ack_seq", "ack"},
                    self.session)
                handoff.accept_activation_ack(
                    response["ack_seq"], _decode(response["ack"]))
                await journal.commit(recorded.nonce)
                committed = True
                return True
            finally:
                handoff.close()
        finally:
            if not committed and user is not None:
                try:
                    # Revoke only this TV's failed enrollment. Never revoke
                    # another TV's concurrent, still-pending authorization.
                    pending = [x for x in journal.snapshot()
                               if x.tv_user_id == user.id
                               and x.tv_uuid == tv.tv_uuid
                               and x.state != "committed"]
                    if len(pending) == 1 and await journal.revoke_unfinished_attempt(
                            pending[0].nonce) and not user.refresh_tokens:
                        await hass.auth.async_remove_user(user)
                except Exception:
                    # Journal remains durable for next HA restart. Do not
                    # falsely claim cleanup if disk/revocation failed.
                    pass
            self.close()

    def close(self) -> None:
        self._barrier.close()
        self._verified_tv = None
        self.code = None


class AiohttpV3LanTransport:
    """Bounded, opt-in, literal LAN IPv4 transport, no redirects or proxies.

    This class intentionally has no constructor that accepts an HTTP URL
    from the TV. HA discovery must separately verify the actual IP/port
    are the owner-selected TV's ephemeral v3 listener.
    """

    def __init__(self, address: str, port: int):
        if type(address) is not str or type(port) is not int or not 1 <= port <= 65535:
            raise ValueError("Invalid TV LAN endpoint")
        try:
            ip = ipaddress.IPv4Address(address)
        except ipaddress.AddressValueError:
            raise ValueError("TV address must be a literal IPv4 address") from None
        if not (ip.is_private or ip.is_link_local) or ip.is_loopback or ip.is_multicast:
            raise ValueError("Not a permitted local TV address")
        self._base = f"http://{ip.compressed}:{port}"

    async def _call(self, method: str, path: str, data: dict) -> dict:
        # Explicit path + method allowlist. The owner-approved credential
        # handoff uses two additional bounded POST-only TV endpoints; without
        # these, the verified pairing succeeds but login aborts before stage.
        post_routes = {
            "/v3/pair/commit", "/v3/pair/reveal-ha",
            "/v3/pair/owner-receipt",
            "/v3/pair/token-stage", "/v3/pair/token-commit",
        }
        get_routes = {"/v3/pair/reveal-tv", "/v3/pair/status"}
        if not ((method == "POST" and path in post_routes)
                or (method == "GET" and path in get_routes)):
            raise ValueError("Unrecognized v3 route")
        import aiohttp
        timeout = aiohttp.ClientTimeout(total=3)
        async with aiohttp.ClientSession(
            timeout=timeout, trust_env=False,
            cookie_jar=aiohttp.DummyCookieJar()
        ) as client:
            async with client.request(
                method, self._base + path,
                json=data if method == "POST" else None,
                params=data if method == "GET" else None,
                allow_redirects=False
            ) as response:
                if response.status != 200:
                    raise ValueError("TV v3 pairing rejected")
                body = await response.content.read(_MAX_BODY + 1)
                if len(body) > _MAX_BODY:
                    raise ValueError("Oversized v3 TV message")
                try:
                    decoded = json.loads(body)
                except (ValueError, UnicodeDecodeError):
                    raise ValueError("Invalid v3 response JSON") from None
                if type(decoded) is not dict:
                    raise ValueError("Invalid v3 response object")
                return decoded

    async def post(self, path: str, data: dict) -> dict:
        return await self._call("POST", path, data)

    async def get(self, path: str, params: dict) -> dict:
        return await self._call("GET", path, params)
