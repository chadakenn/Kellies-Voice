"""Cross-runtime TV/HA #193 test harness using real HA #221 Python implementation.

This script only exchanges SYNTHETIC NON-SECRET test data. It never makes
network requests or invokes /auth/token. 'generate' writes two packets from
the actual HASealedHandoff Python class; Android JVM decrypts them, emits
both receipts; 'verify' replays those Java receipts through a fresh Python
HASealedHandoff, testing authenticating GCM and the full state sequence.

Run this against the exact HA #221 commit pinned in unit-tests.yml.
"""
from __future__ import annotations

import argparse
import base64
import importlib
import json
from pathlib import Path
import sys
import types

from cryptography.hazmat.primitives.asymmetric import ec

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
TV_UUID = "123e4567-e89b-42d3-a456-426614174000"
START = 1700000000
CANDIDATE = b"SYNTHETIC NON-SECRET TEST PAYLOAD"


def _b64(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def _unb64(value: str) -> bytes:
    if not isinstance(value, str) or not value:
        raise AssertionError("Missing interop packet")
    raw = base64.urlsafe_b64decode(value + "==")
    if _b64(raw) != value:
        raise AssertionError("Noncanonical interop packet")
    return raw


def _ha_modules(root: Path):
    folder = root / "custom_components" / "kellies_voice"
    if not (folder / "pairing_handoff_state.py").is_file():
        raise RuntimeError("Pinned HA #221 coordinator missing")
    package = types.ModuleType("interop_ha_peer")
    package.__path__ = [str(folder)]
    sys.modules[package.__name__] = package
    handoff = importlib.import_module("interop_ha_peer.pairing_handoff_state")
    channel = importlib.import_module("interop_ha_peer.pairing_channel")
    verification = importlib.import_module("interop_ha_peer.pairing_verification")
    return handoff, channel, verification


def _session(root: Path):
    handoff, channel, verification = _ha_modules(root)
    tv = ec.derive_private_key(1, ec.SECP256R1())
    ha = ec.derive_private_key(2, ec.SECP256R1())
    tv_pub = verification.public_der(tv)
    ha_pub = verification.public_der(ha)
    ha_channel = channel.PairingSealedChannel(
        ha, local_is_tv=False, session=SESSION, tv_key=tv_pub, ha_key=ha_pub)
    coordinator = handoff.HASealedHandoff(
        ha_channel, owner_user_id="synthetic-owner",
        tv_uuid=TV_UUID, session=SESSION,
        tv_key=tv_pub, ha_key=ha_pub, clock=lambda: START)
    return handoff, coordinator, channel.PairingSealedChannel(
        tv, local_is_tv=True, session=SESSION, tv_key=tv_pub, ha_key=ha_pub)


def generate(root: Path, fixture: Path):
    _module, coordinator, synthetic_tv = _session(root)
    offer = coordinator.credential_offer(CANDIDATE)
    # To advance Python's sender to the HA authorization packet, produce
    # an authenticated synthetic TV receipt in Python. The actual Java
    # receipt will be verified separately in 'verify', not trusted here.
    synthetic_tv.open(offer)
    fields = coordinator._base("tv_proof_receipt")
    fields.update(ack_for="credential_offer", assist_ok=True, alerts_ok=True)
    coordinator.accept_tv_proof(synthetic_tv.seal(
        json.dumps(fields, sort_keys=True).encode("ascii")))
    commit = coordinator.commit_authorization()
    synthetic_tv.open(commit)
    fixture.parent.mkdir(parents=True, exist_ok=True)
    fixture.write_text(json.dumps({
        "session": SESSION,
        "tv_uuid": TV_UUID,
        "unix_seconds": START,
        "candidate": _b64(CANDIDATE),
        "ha_offer": _b64(offer),
        "ha_commit": _b64(commit),
    }, sort_keys=True, indent=2) + "\n")
    print("Python HA #221 AES-GCM offer + authorization fixtures generated")


def verify(root: Path, fixture: Path, receipts: Path):
    f = json.loads(fixture.read_text())
    r = json.loads(receipts.read_text())
    assert f["session"] == SESSION and f["tv_uuid"] == TV_UUID
    assert f["unix_seconds"] == START
    assert _unb64(f["candidate"]) == CANDIDATE
    module, coordinator, _synthetic_tv = _session(root)
    coordinator.credential_offer(CANDIDATE)
    tv_proof = _unb64(r["tv_proof"])
    coordinator.accept_tv_proof(tv_proof)
    assert coordinator.stage is module.Stage.READY_TO_COMMIT
    coordinator.commit_authorization()
    persisted = _unb64(r["tv_persisted"])
    coordinator.accept_tv_persisted(persisted)
    assert coordinator.ready_to_release_lease
    # Replay and reflection must fail. A committed coordinator cannot
    # accept the same receipt twice; a new coordinator uses new state.
    try:
        coordinator.accept_tv_persisted(persisted)
    except module.HandoffRejected:
        pass
    else:
        raise AssertionError("Java persisted replay was accepted by Python")
    print("Verified actual Java AES-GCM TV proof and persisted receipts in Python HA #221")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("generate", "verify"))
    parser.add_argument("--ha-root", type=Path, required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--receipts", type=Path)
    args = parser.parse_args()
    if args.mode == "generate":
        generate(args.ha_root, args.fixture)
    else:
        if args.receipts is None:
            parser.error("--receipts is required for verify")
        verify(args.ha_root, args.fixture, args.receipts)


if __name__ == "__main__":
    main()
