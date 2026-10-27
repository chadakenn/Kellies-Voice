"""Regression checks for the opt-in typed Home Assistant Assist path."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/example/tvvoiceprobe"


class TypedAssistFeatureTests(unittest.TestCase):
    def test_per_tv_setting_defaults_off_and_button_is_hidden(self):
        prefs = (APP / "OverlayPreferences.java").read_text()
        activity = (APP / "VoiceActivity.java").read_text()
        self.assertIn('KEY_TYPED_INPUT = "typed_assist_opt_in"', prefs)
        self.assertIn("preferences.getBoolean(KEY_TYPED_INPUT, false)", prefs)
        self.assertIn("prefs.setTypedAssistEnabled(true);", activity)
        self.assertIn("prefs.setTypedAssistEnabled(false);", activity)
        self.assertIn("typeButton.setVisibility(enabled ? View.VISIBLE : View.GONE)", activity)
        self.assertIn("if (typedInputDialogOpen) return super.dispatchKeyEvent(event);", activity)
        self.assertIn("new InputFilter.LengthFilter(TypedAssistInput.MAX_LENGTH)", activity)
        self.assertIn("if (assist.submitTyped(prompt))", activity)
        self.assertIn("Typed questions go to your Home Assistant Assist agent", activity)
        self.assertIn("No microphone is opened for typed requests.", activity)

    def test_typed_run_starts_at_intent_never_acquires_microphone(self):
        source = (APP / "HaAssistClient.java").read_text()
        start = source.index("public synchronized boolean submitTyped(String raw)")
        end = source.index("public void finishTalking()", start)
        method = source[start:end]
        for required in (
            'request.put("type", "assist_pipeline/run")',
            'request.put("start_stage", "intent")',
            'request.put("end_stage", "tts")',
            'new JSONObject().put("text", text)',
            'if (!new OverlayPreferences(context).typedAssistEnabled())',
            'if (currentRunId != -1)',
            'request.put("timeout", 60)',
            'request.put("conversation_id", conversationId)',
            'request.put("device_id", registryId)',
        ):
            self.assertIn(required, method)
        for forbidden in (
            "Manifest.permission.RECORD_AUDIO",
            "MicrophoneRouting.preflight",
            "beginCapture(", "runs.open(", "runs.acquire(",
            "listeningFocus.acquire(", "AudioRecord(", "input.put(\"sample_rate\"",
        ):
            self.assertNotIn(forbidden, method)
        self.assertIn('if (typedRun) {', source)
        self.assertIn("if (typedRun) break; // Never open AudioRecord for text-only Assist.", source)
        self.assertIn('put("type", "unsubscribe_events")', source)
        self.assertIn('typedRun = false;', source)
        self.assertIn("if (!worker && !textRun && run >= 0)", source)

    def test_existing_audio_pipeline_is_preserved(self):
        source = (APP / "HaAssistClient.java").read_text()
        self.assertIn('request.put("start_stage", "stt")', source)
        self.assertIn('input.put("sample_rate", 16000)', source)
        self.assertIn("MicrophoneRouting.preflight", source)
        self.assertIn("runs.acquire(id, listeningFocus::acquire)", source)


if __name__ == "__main__":
    unittest.main()
