"""Static guardrails for the signed v0.17 home Quick Actions/captions layout."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (
    ROOT
    / "app/src/main/java/com/example/tvvoiceprobe/VoiceActivity.java"
)


class TvHomeCaptionTests(unittest.TestCase):
    def setUp(self):
        self.src = ACTIVITY.read_text(encoding="utf-8")

    def test_quick_actions_take_idle_center_without_empty_transcript_cards(self):
        self.assertIn('centerArea = new FrameLayout(this);', self.src)
        self.assertIn('homeShortcutRow.setOrientation(LinearLayout.VERTICAL);', self.src)
        self.assertNotIn('homeAnswerCard = new LinearLayout(this);', self.src)
        self.assertNotIn('homeTranscriptCard = new LinearLayout(this);', self.src)
        self.assertIn('homeShortcutRow.addView(row2, rowSpace);', self.src)

    def test_cc_overlay_does_not_take_remote_focus(self):
        self.assertIn('captionOverlay.setFocusable(false);', self.src)
        self.assertIn('captionOverlay.setClickable(false);', self.src)
        self.assertIn('showCaption("Listening…", false);', self.src)
        self.assertIn('showCaption("You said: "', self.src)
        self.assertIn('captionOverlay.animate().cancel();', self.src)

    def test_captions_survive_playback_and_have_text_only_timeout(self):
        self.assertIn('if (!captionSpeechActive)', self.src)
        self.assertIn('captionSpeechActive = true;', self.src)
        self.assertIn('player.setOnCompletionListener', self.src)
        self.assertIn('captionSpeechActive = false;', self.src)
        self.assertIn('mainHandler.postDelayed(hideCaption, 1500L);', self.src)
        self.assertIn('mainHandler.postDelayed(hideCaption, large ? 12000L : 7000L);', self.src)
        self.assertIn('mainHandler.removeCallbacks(hideCaption);', self.src)


if __name__ == "__main__":
    unittest.main()
