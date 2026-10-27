"""Regression: replacing a stale Assist socket clears its in-flight effects.

The Android TV integration needs on-device Wi-Fi roam QA beyond these
source checks. This protects the critical reconnect cleanup contract.
"""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/example/tvvoiceprobe/HaAssistClient.java")


class AssistNetworkRecoveryTests(unittest.TestCase):
    def test_reconnect_drops_pending_layout_writes_and_notifies_voice_overlay(self):
        code = SOURCE.read_text()
        loss = code[code.index("private void connectionLost(WebSocket ws)") :
                    code.index("private synchronized void onNetworkReady()")]
        recovered = code[code.index("private synchronized void onNetworkReady()") :
                         code.index("private synchronized void scheduleReconnect()")]
        self.assertIn("layoutWrites.clear();", loss)
        self.assertIn("layoutWrites.clear();", recovered)
        self.assertIn("stopForRunEnd();", recovered)
        self.assertIn("events.connectionLost();", recovered)
        self.assertIn("if (stale != null) stale.cancel();", recovered)
        self.assertIn("openConnection();", recovered)
        self.assertIn("if (disposed || !reconnectEnabled || savedToken == null) return;", recovered)


if __name__ == "__main__":
    unittest.main()
