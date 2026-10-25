"""Button approvals never directly execute a Home Assistant service."""
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
sys.path.insert(0, str(ROOT))
from alert_actions import ActionLedger
from validation import clean_actions

class ActionButtonTests(unittest.TestCase):
    def test_validates_whitelisted_structure(self):
        self.assertEqual([{"id":"ack","label":"Got it"}],
                         clean_actions([{"id":"ack","label":" Got   it " }]))
        for bad in ({"id":"unlock","label":"Do it","service":"lock.unlock"},
                    {"id":"bad-id","label":"No"}, {"id":"ack","label":"https://bad"}):
            with self.assertRaises(ValueError):
                clean_actions([bad])
        with self.assertRaises(ValueError):
            clean_actions([{"id":str(n),"label":"No"} for n in range(4)])
        with self.assertRaises(ValueError):
            clean_actions([{"id":"ack","label":"OK"}]*2)
        self.assertEqual([], clean_actions(None))

    def test_one_time_token_consumes_and_expires(self):
        now = [100.0]
        ledger = ActionLedger(clock=lambda: now[0])
        a = [{"id":"ack","label":"OK"}]
        token = ledger.issue("living-room", a, 8)
        self.assertEqual(32, len(token))
        self.assertFalse(ledger.claim("other-tv", token, "ack"))
        self.assertFalse(ledger.claim("living-room", token, "dismiss"))
        self.assertFalse(ledger.claim("living-room", "0"*32, "ack"))
        self.assertTrue(ledger.claim("living-room", token, "ack"))
        self.assertFalse(ledger.claim("living-room", token, "ack"))
        older = ledger.issue("living-room", a, 8)
        replacement = ledger.issue("living-room", a, 8)
        self.assertFalse(ledger.claim("living-room", older, "ack"))
        now[0] = 108.0
        self.assertFalse(ledger.claim("living-room", replacement, "ack"))

    def test_cleared_on_new_popup_and_hide(self):
        ledger = ActionLedger()
        first = ledger.issue("tv", [{"id":"ack","label":"OK"}], 30)
        ledger.issue("tv", [], 30)
        self.assertFalse(ledger.claim("tv", first, "ack"))
        second = ledger.issue("tv", [{"id":"ack","label":"OK"}], 30)
        ledger.clear("tv")
        self.assertFalse(ledger.claim("tv", second, "ack"))

if __name__ == "__main__":
    unittest.main()
