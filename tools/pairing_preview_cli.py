#!/usr/bin/env python3
"""#193: optional, standalone two-screen LAN preview for the TV test APK.

NO HA LOGIN, OAuth, credentials, persistent files, REST API tokens or
production HA integration changes. Run only while the Pairing Lab app's
120-second START DISCOVERY PREVIEW window is visible on the TV.

The user's physical comparison of the SAME eight digits on TV and this
terminal, followed by a real remote OK, is mandatory. A matching cryptographic
code by itself does not authenticate a remote LAN computer.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import ipaddress
import json
import re
import sys
import time
from dataclasses import dataclass
from urllib import request, error
from uuid import UUID

TYPE = "_kellies-pair._tcp.local."
SESSION = re.compile(r"[A-Za-z0-9_-]{22}\Z")
RFC1918 = [ipaddress.ip_network(n) for n in (
    "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"
)]
TRANSCRIPT_DOMAIN = b"KVPair/v1/transcript"
CODE_DOMAIN = b"KelliesVoice/v1/visual-compare"
PROOF_DOMAIN = b"KelliesVoice/v2/tv-approved-uuid"


@dataclass(frozen=True)
class Candidate:
    address: str
    port: int
    session: str


def _sid(session: str) -> bytes:
    if not isinstance(session, str) or not SESSION.fullmatch(session):
        raise ValueError("Invalid pairing session")
    try:
        result = base64.urlsafe_b64decode(session + "==")
    except Exception as exc:
        raise ValueError("Invalid base64 session") from exc
    if len(result) != 16 or _b64(result) != session:
        raise ValueError("Noncanonical 16-byte session")
    return result


def _b64(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _unb64(value: str) -> bytes:
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]+", value):
        raise ValueError("Invalid base64url field")
    try:
        raw = base64.urlsafe_b64decode(value + "==")
    except Exception as exc:
        raise ValueError("Invalid base64url field") from exc
    if _b64(raw) != value:
        raise ValueError("Noncanonical base64url field")
    return raw


def candidate(address: str, port: int, props: dict) -> Candidate:
    try:
        ip = ipaddress.ip_address(address)
    except (ValueError, TypeError) as exc:
        raise ValueError("Untrusted pairing IP") from exc
    if not isinstance(ip, ipaddress.IPv4Address) or not any(ip in net for net in RFC1918):
        raise ValueError("Pairing address must be private IPv4")
    if type(port) is not int or not 1024 <= port <= 65535:
        raise ValueError("Untrusted pairing port")
    if type(props) is not dict:
        raise ValueError("Bad pairing TXT record")

    def prop(key):
        # python-zeroconf reports TXT property keys as bytes; tests and
        # curated sources may use strings. Accept either exact spelling.
        value = props.get(key, props.get(key.encode("ascii")))
        if isinstance(value, bytes):
            try:
                return value.decode("ascii")
            except UnicodeDecodeError:
                return ""
        return value if isinstance(value, str) else ""

    if prop("protocol") != "1" or prop("mode") != "discovery-only":
        raise ValueError("Not a discovery-only pairing service")
    session = prop("session")
    _sid(session)
    return Candidate(str(ip), port, session)


def _part(content: bytes) -> bytes:
    if len(content) > 65535:
        raise ValueError("Oversized pairing field")
    return len(content).to_bytes(2, "big") + content


def _p256_public(data: bytes):
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.serialization import (
        Encoding, PublicFormat, load_der_public_key,
    )
    if type(data) is not bytes or not 70 <= len(data) <= 150:
        raise ValueError("Untrusted P-256 key size")
    public = load_der_public_key(data)
    if not isinstance(public, ec.EllipticCurvePublicKey) or not isinstance(
        public.curve, ec.SECP256R1
    ) or not hmac.compare_digest(
        data, public.public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
    ):
        raise ValueError("Untrusted or noncanonical P-256 public key")
    return public


def _context(private, session, tv_der, ha_der, tv_uuid=None):
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
    if (private.public_key().public_bytes(
        Encoding.DER, PublicFormat.SubjectPublicKeyInfo) != ha_der
        or hmac.compare_digest(tv_der, ha_der)
    ):
        raise ValueError("Incorrect pairing peer role")
    peer = _p256_public(tv_der)
    _p256_public(ha_der)
    transcript = (
        TRANSCRIPT_DOMAIN + _part(_sid(session))
        + _part(tv_der) + _part(ha_der)
    )
    if tv_uuid is not None:
        try:
            uuid = UUID(tv_uuid)
        except Exception as exc:
            raise ValueError("Invalid TV identity") from exc
        if (str(uuid) != tv_uuid or uuid.version != 4 or uuid.variant !=
                "specified in RFC 4122"):
            raise ValueError("Noncanonical TV v4 UUID")
        transcript += _part(tv_uuid.encode("ascii"))
    shared = private.exchange(ec.ECDH(), peer)
    salt = hashlib.sha256(transcript).digest()
    return hmac.new(salt, shared, hashlib.sha256).digest()


def comparison_code(private, session, tv_der, ha_der) -> str:
    prk = _context(private, session, tv_der, ha_der)
    result = hmac.new(prk, CODE_DOMAIN + b"\x01", hashlib.sha256).digest()
    return f"{int.from_bytes(result[:4], 'big') % 100000000:08d}"


def approved_proof(private, session, tv_der, ha_der, tv_uuid) -> str:
    prk = _context(private, session, tv_der, ha_der, tv_uuid)
    output = hmac.new(prk, PROOF_DOMAIN + b"\x01", hashlib.sha256).digest()[:16]
    return _b64(output)


def discover(seconds: int) -> list[Candidate]:
    from zeroconf import ServiceBrowser, ServiceStateChange, Zeroconf
    results: dict[tuple, Candidate] = {}
    started = time.monotonic()
    with Zeroconf() as zc:
        # python-zeroconf dispatches handler callbacks by KEYWORD, not
        # solely positional arguments. Names must match its public API
        # (zeroconf, service_type, name, state_change) exactly.
        def on_change(zeroconf, service_type, name, state_change):
            if state_change not in (
                ServiceStateChange.Added, ServiceStateChange.Updated
            ):
                return
            info = zeroconf.get_service_info(service_type, name, timeout=1000)
            if info is None:
                return
            for ip in info.parsed_addresses():
                try:
                    found = candidate(ip, info.port, info.properties)
                except ValueError:
                    continue
                results[(found.address, found.port, found.session)] = found
        browser = ServiceBrowser(zc, TYPE, handlers=[on_change])
        try:
            while time.monotonic() - started < seconds:
                if results:
                    # One second allows multiple TV entries to appear.
                    time.sleep(1)
                    break
                time.sleep(0.25)
        finally:
            browser.cancel()
    return list(results.values())


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Pairing HTTP redirects are forbidden")


def http_json(peer: Candidate, path: str, body: dict | None = None) -> dict:
    # The peer has been validated. Never accept an untrusted URL from TXT/JSON.
    candidate(peer.address, peer.port, {
        "protocol": "1", "mode": "discovery-only", "session": peer.session})
    if path not in ("/v1/pair/capabilities", "/v1/pair/offer",
                    "/v1/pair/status?session=" + peer.session):
        raise ValueError("Unapproved pairing endpoint")
    encoded = None if body is None else json.dumps(body).encode("ascii")
    req = request.Request(
        f"http://{peer.address}:{peer.port}{path}", data=encoded,
        headers={"Content-Type": "application/json"} if body is not None else {},
        method="POST" if body is not None else "GET",
    )
    opener = request.build_opener(request.ProxyHandler({}), NoRedirect())
    with opener.open(req, timeout=3) as response:
        if response.status != 200:
            raise ValueError("TV rejected pairing preview request")
        data = response.read(513)
        if len(data) > 512:
            raise ValueError("Pairing response too large")
    obj = json.loads(data.decode("utf-8", errors="strict"))
    if type(obj) is not dict:
        raise ValueError("Invalid TV pairing response")
    return obj


def choose(items: list[Candidate]) -> Candidate:
    if not items:
        raise ValueError("No TV Pairing Lab announcement detected; check Wi-Fi/VLAN multicast")
    if len(items) == 1:
        return items[0]
    print("Multiple temporary TVs detected. Choose only the TV you started:")
    for i, value in enumerate(items, start=1):
        print(f"  {i}. {value.address}:{value.port}")
    answer = input("TV number: ").strip()
    if not answer.isdecimal() or not 1 <= int(answer) <= len(items):
        raise ValueError("No TV selected")
    return items[int(answer) - 1]


def run(preview: Candidate, timeout: int) -> None:
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

    started = time.monotonic()
    capabilities = http_json(preview, "/v1/pair/capabilities")
    if capabilities != {
        "protocol": 1, "mode": "discovery-only",
        "authorized": False, "session": preview.session
    }:
        raise ValueError("Unexpected TV capabilities")

    ha = ec.generate_private_key(ec.SECP256R1())
    ha_der = ha.public_key().public_bytes(
        Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
    reply = http_json(preview, "/v1/pair/offer", {
        "session": preview.session, "ha_key": _b64(ha_der)
    })
    if set(reply) != {"session", "authorized", "tv_key"} or (
        reply["session"] != preview.session or reply["authorized"] is not False
    ):
        raise ValueError("TV declined or changed pairing session")
    tv_der = _unb64(reply["tv_key"])
    code = comparison_code(ha, preview.session, tv_der, ha_der)
    print("\n" + "=" * 50)
    print("HOME ASSISTANT PAIRING PREVIEW CODE:")
    print(f"             {code}")
    print("=" * 50)
    print("Look at the TV. The SAME eight digits must appear there.")
    print("If they differ: CANCEL on TV, then Ctrl+C here.")
    print("If they match: press CONFIRM ON TV using the physical TV remote.")
    input("Only AFTER pressing TV confirm, press Enter here to verify: ")
    end = min(started + 100, time.monotonic() + 20)
    while time.monotonic() < end:
        reply = http_json(
            preview, "/v1/pair/status?session=" + preview.session)
        if reply == {"approved": False}:
            time.sleep(1)
            continue
        if set(reply) != {"approved", "proof", "tv_uuid"} or reply["approved"] is not True:
            raise ValueError("TV approval response invalid")
        proof = _unb64(reply["proof"])
        expected = _unb64(approved_proof(
            ha, preview.session, tv_der, ha_der, reply["tv_uuid"]))
        if len(proof) != 16 or not hmac.compare_digest(proof, expected):
            raise ValueError("TV OK proof failed: identity or cryptographic mismatch")
        print("\nPASS: Physical TV remote OK and P-256 ECDH TV identity verified.")
        print("NO Home Assistant account was authorized or changed.")
        return
    raise ValueError("No TV OK proof received before the 120-second window expired")


def main() -> int:
    parser = argparse.ArgumentParser(description="Kellie's Voice TV LAN pairing preview, no tokens")
    parser.add_argument("--discover-seconds", type=int, default=40)
    args = parser.parse_args()
    if not 1 <= args.discover_seconds <= 90:
        parser.error("--discover-seconds must be 1 to 90")
    print("Start the 120-second DISCOVERY PREVIEW on the TV now.")
    print("Scanning for _kellies-pair._tcp.local. (does not change Home Assistant)...")
    try:
        items = discover(args.discover_seconds)
        peer = choose(items)
        print("Found one authorized-for-preview TV candidate; requesting public key only.")
        run(peer, args.discover_seconds)
        return 0
    except (OSError, ValueError, TimeoutError, error.URLError, ImportError) as exc:
        print("FAIL:", exc, file=sys.stderr)
        print("No login or token was changed.", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\nCancelled. No credentials were changed.")
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
