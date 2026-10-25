"""Build-time regression guard for Android TTS package visibility.

The app targets API >=30, so the Android 11 TTS package visibility
declaration must remain in the manifest. The actual speaking path
still requires an Android TV hardware test.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class TextToSpeechManifestTests(unittest.TestCase):
    def test_tts_engine_service_query_is_declared(self):
        root = ET.parse(ROOT / "app" / "src" / "main" / "AndroidManifest.xml").getroot()
        queries = root.find("queries")
        self.assertIsNotNone(queries)
        actions = [action.attrib.get(ANDROID + "name")
                   for action in queries.findall("./intent/action")]
        self.assertIn("android.intent.action.TTS_SERVICE", actions)

    def test_speech_status_does_not_record_event_text(self):
        path = ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "tvvoiceprobe"
        source = (path / "AnnouncementSpeech.java").read_text(encoding="utf-8")
        self.assertIn("TvSpeechStatus.note", source)
        self.assertIn("Android TTS initialization failed", source)
        self.assertNotIn("TvSpeechStatus.note(phrase)", source)


if __name__ == "__main__":
    unittest.main()
