"""Regression: TV diagnostic reports require actual popup creation and scope."""

from pathlib import Path
import ast
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/example/tvvoiceprobe/TvEventsService.java"
BACK = ROOT / "custom_components/kellies_voice"


class WiredTvDiagnosticTests(unittest.TestCase):
    def test_new_ha_sensor_platform_keeps_existing_receiver_diagnostic(self):
        const = (BACK / "const.py").read_text()
        self.assertIn('"binary_sensor", "sensor"', const)
        binary = (BACK / "binary_sensor.py").read_text()
        self.assertIn("TV alert receiver connected", binary)
        self.assertIn('"message_delivery_confirmed": False', binary)
        source = (BACK / "sensor.py").read_text()
        self.assertIn("TV app version (reported)", source)
        self.assertIn("TV last popup receipt", source)
        self.assertIn('not_proof_of_visible_pixels": True', source)

    def test_receipts_from_authenticated_scoped_socket_only(self):
        ws = (BACK / "websocket.py").read_text()
        self.assertIn('websocket_api.async_register_command(hass, websocket_tv_diagnostic)', ws)
        self.assertIn("if not _tv_can_read(hass, connection.user, device_id):", ws)
        self.assertIn("diagnostics(hass).issue(device_id, connection_key, event.event_type)", ws)
        self.assertIn('payload["diagnostic_receipt"] = receipt', ws)
        self.assertIn("diagnostics(hass).unsubscribe(device_id, connection_key)", ws)
        self.assertIn("diagnostics(hass).subscribe(device_id, connection_key, connection)", ws)
        self.assertIn("device_id, connection, msg[\"stage\"]", ws)
        self.assertNotIn('payload["announcement_text"]', ws)

    def test_android_sends_version_after_subscription_and_acks_actual_popup(self):
        code = SOURCE.read_text()
        self.assertIn("alertsSubscribed = true;\n                        reportDiagnosticHello();", code)
        self.assertIn('installed.versionName', code)
        self.assertIn('installed.getLongVersionCode()', code)
        self.assertIn('diagnosticReceipt = data.optString("diagnostic_receipt", "")', code)
        self.assertIn('reportPopupReceipt("received", diagnosticReceipt)', code)
        self.assertIn('reportPopupReceipt("popup_created", diagnosticReceipt)', code)
        self.assertIn('if (card != null) reportPopupReceipt("popup_created", diagnosticReceipt)', code)
        self.assertIn('if (!alertsSubscribed || legacyMode || socket == null || stopping', code)
        self.assertIn('receipt.matches("[0-9a-f]{24}")', code)
        self.assertNotIn("reportPopupReceipt(\"popup_created\", diagnosticReceipt);\n            // A close", code)

    def test_python_modules_compile(self):
        for path in (BACK / "alert_diagnostics.py", BACK / "sensor.py", BACK / "websocket.py"):
            ast.parse(path.read_text())


if __name__ == "__main__":
    unittest.main()
