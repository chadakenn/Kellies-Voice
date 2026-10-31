"""Offline P-256 AES-GCM scoped renewable-login transfer and 2-phase ACK."""
from __future__ import annotations
from dataclasses import replace
import importlib
import sys
import types
import unittest
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError:
    ec = None

ROOT = (Path(__file__).resolve().parents[1] /
        "custom_components/kellies_voice_v3qa")
PKG = "kv_v3_scoped_login_transport_tests"
pkg = types.ModuleType(PKG)
pkg.__path__ = [str(ROOT)]
sys.modules[PKG] = pkg
if ec is not None:
    keys = importlib.import_module(PKG + ".pairing_key_commitment")
    crypto = importlib.import_module(PKG + ".pairing_v3_verified")
    wire = importlib.import_module(PKG + ".pairing_v3_wire")
    login = importlib.import_module(PKG + ".scoped_login_issuer")
    handoff = importlib.import_module(PKG + ".scoped_token_handoff")
    lease_mod = importlib.import_module(PKG + ".token_lease_journal")

SID = "AQIDBAUGBwgJCgsMDQ4PEA"
TV = "123e4567-e89b-42d3-a456-426614174000"
OTHER = "123e4567-e89b-42d3-a456-426614174001"
ORIGIN = "https://ha.example.test"
NONCE = "0123456789abcdef0123456789abcdef"


def pair(tv_uuid=TV):
    tv_key = ec.derive_private_key(1, ec.SECP256R1())
    ha_key = ec.derive_private_key(2, ec.SECP256R1())
    tv = keys.CommitmentSession("tv", SID, crypto.public_der(tv_key),
                                bytes(range(32)))
    ha = keys.CommitmentSession("ha", SID, crypto.public_der(ha_key),
                                bytes(range(32, 64)))
    tv.receive_peer_commit(ha.public_commit)
    ha.receive_peer_commit(tv.public_commit)
    tv_public, tv_nonce = tv.reveal()
    ha_public, ha_nonce = ha.reveal()
    tv.receive_peer_reveal(ha_public, ha_nonce)
    ha.receive_peer_reveal(tv_public, tv_nonce)
    result = wire.VerifiedTv(tv_uuid=tv_uuid, session=SID)
    # The real caller also must verify physical remote and actual HA owner
    # before it even reaches these offline codec constructors.
    ha_peer = handoff.HaTokenHandoff(
        private_key=ha_key, verified=ha, tv=result, now=100)
    tv_peer = handoff.TvTokenHandoff(
        private_key=tv_key, verified=tv, tv=result,
        expected_ha_origin=ORIGIN, now=100)
    return ha_peer, tv_peer


def staged_lease():
    start = lease_mod.TokenLease(
        tv_uuid=TV, tv_user_id="restricted-tv-account",
        owner_id="actual-ha-owner",
        client_id=ORIGIN + "/kellies-voice/tv/" + TV + "/attempt/" + NONCE,
        nonce=NONCE, state="intent")
    issued = login.IssuedTvCredential(
        lease=start, access_token="SECRET-ACCESS-TOKEN",
        refresh_token="SECRET-REFRESH-TOKEN", access_token_seconds=1800,
    )
    recorded = replace(start, state="issued", token_id="recorded-token-id")
    return issued, recorded


@unittest.skipUnless(ec is not None, "Optional cryptography absent in general HA CI")
class ScopeTransferTests(unittest.TestCase):
    def test_full_encrypted_stage_commit_activate_with_distinct_signed_acks(self):
        ha, tv = pair()
        issued, record = staged_lease()
        seq, envelope = ha.seal_recorded_provisional(
            issued, record, now=101)
        self.assertEqual(seq, 1)
        self.assertNotIn(issued.refresh_token.encode(), envelope)
        self.assertNotIn(issued.access_token.encode(), envelope)
        grant = tv.open_provisional(seq, envelope, now=101)
        self.assertEqual(grant.refresh_token, issued.refresh_token)
        self.assertEqual(grant.access_token, issued.access_token)
        self.assertEqual(grant.token_id, record.token_id)
        # In a real app, stage the grant in Android Keystore now.
        step, signed_stage = tv.stage_ack(now=102)
        ha.accept_stage_ack(step, signed_stage, now=102)
        next_seq, ready = ha.seal_commit_ready(now=103)
        self.assertEqual(next_seq, 2)
        self.assertTrue(tv.accept_commit_ready(next_seq, ready, now=103))
        # Only now could the TV activate the staged Keystore entry.
        ack_seq, activated = tv.activation_ack(now=104)
        self.assertTrue(ha.accept_activation_ack(ack_seq, activated, now=104))
        self.assertTrue(ha._closed)
        self.assertTrue(tv._closed)

    def test_prevents_provisional_use_without_recorded_unique_token(self):
        ha, tv = pair()
        issued, record = staged_lease()
        with self.assertRaises(handoff.TokenHandoffDenied):
            ha.seal_recorded_provisional(
                issued, replace(record, token_id=None), now=101)
        self.assertTrue(ha._closed)

    def test_expiry_wrong_tv_nonce_and_tampered_ciphertext_fail_closed(self):
        ha, tv = pair()
        issued, record = staged_lease()
        seq, packet = ha.seal_recorded_provisional(issued, record, now=101)
        with self.assertRaises(handoff.TokenHandoffDenied):
            tv.open_provisional(seq, packet[:-1] +
                                bytes((packet[-1] ^ 1,)), now=101)
        self.assertTrue(tv._closed)
        ha2, tv2 = pair(tv_uuid=OTHER)
        with self.assertRaises(handoff.TokenHandoffDenied):
            tv2.open_provisional(seq, packet, now=101)
        ha3, tv3 = pair()
        with self.assertRaises(handoff.TokenHandoffDenied):
            ha3.seal_recorded_provisional(issued, record, now=160)

    def test_no_commit_without_staged_ack_and_no_replays(self):
        ha, tv = pair()
        issued, record = staged_lease()
        seq, encrypted = ha.seal_recorded_provisional(
            issued, record, now=101)
        tv.open_provisional(seq, encrypted, now=101)
        with self.assertRaises(handoff.TokenHandoffDenied):
            ha.seal_commit_ready(now=101)
        self.assertTrue(ha._closed)

        ha, tv = pair()
        seq, encrypted = ha.seal_recorded_provisional(
            issued, record, now=101)
        tv.open_provisional(seq, encrypted, now=101)
        ack_seq, stage_ack = tv.stage_ack(now=102)
        ha.accept_stage_ack(ack_seq, stage_ack, now=102)
        with self.assertRaises(handoff.TokenHandoffDenied):
            ha.accept_stage_ack(ack_seq, stage_ack, now=102)

    def test_wrong_origin_refused_even_after_successful_gcm_decrypt(self):
        ha, tv = pair()
        issued, record = staged_lease()
        seq, encrypted = ha.seal_recorded_provisional(
            issued, record, now=101)
        wrong_ha, wrong_tv = pair()
        wrong_tv._trusted_origin = "https://evil.example.test"
        with self.assertRaises(handoff.TokenHandoffDenied):
            wrong_tv.open_provisional(seq, encrypted, now=101)


if __name__ == "__main__":
    unittest.main()
