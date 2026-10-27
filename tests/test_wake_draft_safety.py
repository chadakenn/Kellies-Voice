"""Wake-word draft safety checks while the architecture awaits a dev rebase."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/example/tvvoiceprobe"
CMAKE = ROOT / "app/src/main/cpp/CMakeLists.txt"


class WakeDraftSafetyTests(unittest.TestCase):
    def test_missing_audio_device_and_permission_revoke_stop_wake_listener(self):
        service = (SRC / "WakeWordService.java").read_text()
        self.assertIn("if (read < 0)", service)
        self.assertIn('if (read == 0) { sleep(30); continue; }', service)
        self.assertIn("checkSelfPermission(Manifest.permission.RECORD_AUDIO)", service)
        self.assertIn("!android.provider.Settings.canDrawOverlays(this)", service)
        self.assertIn("prefs.setWakeWord(false);", service)

    def test_forget_login_disarms_wake_before_clearing_credentials(self):
        app = (SRC / "VoiceActivity.java").read_text()
        forget = app[app.index("private void confirmForget()"):]
        self.assertLess(forget.index("prefs.setWakeWord(false);"),
                        forget.index("store.clear();"))
        self.assertLess(forget.index("stopService(new Intent(this, WakeWordService.class));"),
                        forget.index("store.clear();"))

    def test_failure_notice_survives_foreground_notification_removal(self):
        service = (SRC / "WakeWordService.java").read_text()
        self.assertIn("FAILURE_NOTICE_ID = 2252", service)
        self.assertLess(service.index("stopForeground(STOP_FOREGROUND_REMOVE);"),
                        service.index("if (failureNotice != null) publishFailure(failureNotice);"))
        self.assertIn("manager.notify(FAILURE_NOTICE_ID,", service)
        self.assertIn("new CredentialStore(this).load() != null", service)

    def test_wake_preconnect_has_no_audio_and_obeys_opt_in(self):
        warm = (SRC / "WakeAssistConnection.java").read_text()
        wake = (SRC / "WakeWordService.java").read_text()
        overlay = (SRC / "VoiceOverlayService.java").read_text()
        client = (SRC / "HaAssistClient.java").read_text()

        # A warm socket authenticates only. It cannot open an Assist run or
        # a microphone, and must not be established from an app boot handler.
        self.assertIn("new HaAssistClient(context.getApplicationContext(), QUIET)", warm)
        self.assertIn("if (!prefs.wakeWord() || prefs.microphoneDisabled()) return;", warm)
        self.assertIn("!WakeWordService.isListening()", warm)
        self.assertNotIn("AudioRecord(", warm)
        self.assertNotIn(".talk(", warm)
        self.assertIn("WakeAssistConnection.preconnect(this);", wake)
        self.assertIn("WakeAssistConnection.stop();", wake)
        self.assertIn("WakeAssistConnection.take(this)", overlay)
        self.assertIn("fromWake ? WakeAssistConnection.take(this) : null", overlay)
        self.assertIn("assist.isConnected()", overlay)
        self.assertIn("created.useAsStandby()", warm)
        self.assertIn("client.attachEvents(receiver)", warm)
        self.assertIn("if (VoiceOverlayService.isOpen()) return;", warm)
        self.assertIn("authReady.onAuthenticated(uiOwner)", client)
        self.assertIn("WakeCommandLead.MAX_MS", (SRC / "WakeWordService.java").read_text())
        self.assertIn("void setEvents(Events receiver)", client)

        # No audio session may be returned to background warm ownership.
        self.assertLess(overlay.index("if (assist != null) { assist.shutdown(); assist = null; }"),
                        overlay.rindex("WakeAssistConnection.preconnect(this);"))

    def test_ducking_is_opt_in_wake_only_with_careful_volume_restore(self):
        wake = (SRC / "WakeWordService.java").read_text()
        overlay = (SRC / "VoiceOverlayService.java").read_text()
        ui = (SRC / "VoiceActivity.java").read_text()
        prefs = (SRC / "OverlayPreferences.java").read_text()
        duck = (SRC / "WakeAudioDucking.java").read_text()
        levels = (SRC / "WakeDuckingLevels.java").read_text()
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()

        self.assertIn('getInt(KEY_WAKE_DUCK, 0)', prefs)
        self.assertIn('WakeDuckingLevels.normalize(percent)', prefs)
        self.assertIn('{0, 25, 50, 75}', levels)
        self.assertIn('private void chooseWakeAudioDucking()', ui)
        self.assertIn('Wake audio ducking:', ui)
        self.assertIn('WakeAudioDucking.begin(this, prefs.wakeDuckingPercent());', wake)
        self.assertLess(wake.index('session.onChunk(engine.processAudio(samples))'),
                        wake.index('WakeAudioDucking.begin(this, prefs.wakeDuckingPercent());'))
        self.assertIn('WakeAudioDucking.end(this);', wake)
        self.assertIn('WakeAudioDucking.end(this);', overlay)
        self.assertIn('WakeAudioDucking.recover(this);', ui)
        self.assertIn('if (active) return;', duck)
        self.assertIn('getStreamVolume(AudioManager.STREAM_MUSIC) == applied', duck)
        self.assertIn('getBoolean(PENDING, false)', duck)
        self.assertIn('android.permission.MODIFY_AUDIO_SETTINGS', manifest)
        self.assertNotIn('WakeAudioDucking.begin(', overlay)

    def test_boot_never_starts_mic_but_foreground_open_restores_explicit_opt_in(self):
        startup = (SRC / "WakeWordStartup.java").read_text()
        receiver = (SRC / "TvEventsRestartReceiver.java").read_text()
        activity = (SRC / "VoiceActivity.java").read_text()
        session = (SRC / "WakeWordSession.java").read_text()
        prefs = (SRC / "OverlayPreferences.java").read_text()
        wake = (SRC / "WakeWordService.java").read_text()
        self.assertIn("WakeWordStartup.onDeviceRestart(context);", receiver)
        self.assertNotIn("WakeWordService.class", receiver)
        self.assertIn("showResumeReminder(context);", startup)
        self.assertIn("new Intent(context, VoiceActivity.class)", startup)
        self.assertIn("!prefs.wakeRestartConsent()", startup)
        self.assertIn("prefs.wakeRestartConsent()", startup)
        self.assertIn("wake_word_restart_consent", prefs)
        self.assertIn("getBoolean(KEY_WAKE_RESTART, false)", prefs)
        self.assertIn(".putBoolean(KEY_WAKE_RESTART, enabled)", prefs)
        self.assertIn("ACTION_RESTORE", session)
        self.assertIn("WakeWordStartup.resumeFromVisible(this);", activity)
        self.assertIn("activity.startForegroundService(", startup)
        self.assertIn("WakeWordStartup.clearReminder(this);", wake)
        self.assertIn("prefs.setWakeWord(false);", activity)
        self.assertIn("WakeWordStartup.clearReminder(this);", activity)

    def test_sideloadable_debug_build_has_no_hwasan_runtime_requirement(self):
        cmake = CMAKE.read_text()
        self.assertNotIn("-fsanitize=hwaddress", cmake)


if __name__ == "__main__":
    unittest.main()
