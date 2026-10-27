"""Pipeline picker regression: no global HA preference changes, no mic side effects."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/example/tvvoiceprobe"


class AssistPipelinePickerTests(unittest.TestCase):
    def test_voice_run_uses_tv_choice_without_changing_ha_preferred(self):
        client = (APP / "HaAssistClient.java").read_text()
        prefs = (APP / "OverlayPreferences.java").read_text()
        activity = (APP / "VoiceActivity.java").read_text()
        self.assertIn('put("type", "assist_pipeline/pipeline/list")', client)
        self.assertIn('request.put("pipeline", selectedPipeline)', client)
        self.assertIn('if (!selectedPipeline.isEmpty())', client)
        self.assertIn('request.put("start_stage", "stt")', client)
        self.assertIn("default void pipelines(", client)
        self.assertIn("pipelineListId = -1;", client)
        self.assertNotIn('assist_pipeline/pipeline/set_preferred', client)
        self.assertIn("assistPipelineId()", prefs)
        self.assertIn('KEY_ASSIST_PIPELINE = "assist_pipeline_id"', prefs)
        self.assertIn('case 21: chooseAssistPipeline();', activity)
        self.assertIn('assist.clearConversationContext();', activity)

    def test_settings_only_queries_pipelines_while_connected(self):
        activity = (APP / "VoiceActivity.java").read_text()
        self.assertIn('if (assist == null || !assist.isConnected())', activity)
        self.assertIn('if (!assist.requestPipelines())', activity)
        self.assertIn('Use Home Assistant preferred (default)', activity)
        self.assertIn('Finish the current Assist answer before changing pipelines.', activity)
        self.assertIn('The saved choice was not changed.', activity)


if __name__ == "__main__":
    unittest.main()
