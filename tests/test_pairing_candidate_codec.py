"""Cross-language golden vector for B15; all credentials are test strings.

Reference Android Java PairingCredentialCandidateCodec (TV draft #225).
Neither side sends tokens or changes working login in this test.
"""
import unittest

from custom_components.kellies_voice.pairing_candidate_codec import (
    CredentialCandidate, encode_candidate, decode_candidate, fingerprint, MAX_BYTES,
)

TV = "123e4567-e89b-42d3-a456-426614174000"
SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
REFERENCE_HEX_SHA256 = (
    "a9baf0e2a82d08aca7ddb387b906016a25cd1ee7b5f138b96d0b972ee9440f23"
)


def sample(**changes):
    fields = dict(origin="https://ha.example",
                  client_id="https://ha.example/",
                  access_token="a" * 32,
                  refresh_token="r" * 32,
                  session=SESSION,
                  tv_uuid=TV,
                  access_expires_at_ms=1700001800000,
                  staged_at_ms=1700000000000)
    fields.update(changes)
    return CredentialCandidate(**fields)


class CandidateCodecTests(unittest.TestCase):
    def test_android_reference_vector_and_exact_round_trip(self):
        original = sample()
        encoded = encode_candidate(original)
        self.assertEqual(218, len(encoded))
        self.assertEqual(REFERENCE_HEX_SHA256, fingerprint(encoded).hex())
        decoded = decode_candidate(encoded)
        self.assertEqual(original, decoded)
        self.assertEqual(encoded, encode_candidate(decoded))
        self.assertNotIn("a" * 32, repr(decoded))
        self.assertNotIn("r" * 32, repr(decoded))

    def test_every_credential_binding_affects_fingerprint(self):
        baseline = fingerprint(encode_candidate(sample()))
        changes = (
            dict(origin="https://other.example",
                 client_id="https://other.example/"),
            dict(access_token="b" * 32),
            dict(refresh_token="s" * 32),
            dict(tv_uuid="223e4567-e89b-42d3-a456-426614174000"),
            dict(access_expires_at_ms=1700001800001),
            dict(staged_at_ms=1700000000001),
            dict(session="AQIDBAUGBwgJCgsMDQ4PEQ"),
        )
        for changed in changes:
            with self.subTest(changed=tuple(changed)):
                other = fingerprint(encode_candidate(sample(**changed)))
                self.assertNotEqual(baseline, other)

    def test_noncanonical_invalid_and_oversize_rejected(self):
        encoded = encode_candidate(sample())
        for bad in (b"", b"anything", encoded[:-1], encoded + b"extra",
                    b"x" * (MAX_BYTES + 1), bytes([encoded[0] ^ 1]) + encoded[1:]):
            with self.subTest(length=len(bad)), self.assertRaises(ValueError):
                decode_candidate(bad)
        with self.assertRaises(ValueError):
            encode_candidate(sample(origin="http://public.example",
                                    client_id="http://public.example/"))
        with self.assertRaises(ValueError):
            encode_candidate(sample(access_token="short"))
        with self.assertRaises(ValueError):
            encode_candidate(sample(access_token="a" * 1020,
                                    refresh_token="r" * 1020))
        with self.assertRaises(ValueError):
            fingerprint(b"")

    def test_wrong_client_and_invalid_session_fail_closed(self):
        with self.assertRaises(ValueError):
            encode_candidate(sample(client_id="https://wrong.example/"))
        with self.assertRaises(ValueError):
            encode_candidate(sample(session="not-a-session"))


if __name__ == "__main__":
    unittest.main()
