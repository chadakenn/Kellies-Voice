"""Issue #261: edit the TV's dashboard URL and secure sign-in from Home Assistant.

Source-contract checks run without HA or an Android device. A real HA change
must still be verified with a signed QA TV APK and matching HACS beta.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "app/src/main/java/com/example/tvvoiceprobe"
HA = ROOT / "custom_components/kellies_voice"


def android(name):
    return (ANDROID / name).read_text()


class DashboardHaControlsTests(unittest.TestCase):
    def test_existing_url_entity_renamed_in_place_not_duplicated(self):
        text = (HA / "text.py").read_text()
        self.assertIn(
            'KellieFrigateText(entry, "screensaver_url", "Screensaver dashboard URL"',
            text,
        )
        # Stable setting / unique ID preserve existing HA entity registry state.
        self.assertIn('self._attr_unique_id = f"{self._device_id}_{setting}"', text)
        self.assertEqual(text.count('KellieFrigateText(entry, "screensaver_url"'), 1)
        validator = (HA / "frigate_settings.py").read_text()
        self.assertIn('setting == "screensaver_url"', validator)
        self.assertIn('uri.scheme not in ("http", "https")', validator)
        self.assertIn('Never put an access token or password into the URL.', validator)

    def test_new_per_tv_ha_switch_opt_in_and_no_microphone(self):
        switch = (HA / "switch.py").read_text()
        self.assertIn("KellieHaDashboardSignInSwitch(entry)", switch)
        self.assertIn("class KellieHaDashboardSignInSwitch(KellieAutoListen)", switch)
        self.assertIn('self._setting = "screensaver_ha_external_auth"', switch)
        self.assertIn(
            'self._attr_unique_id = f"{self._device_id}_screensaver_ha_external_auth"',
            switch)
        self.assertIn('self._attr_name = "Secure HA dashboard sign-in"', switch)
        self.assertIn("self._attr_is_on = False", switch)
        self.assertIn("self._generation += 1", switch)
        self.assertIn("self.async_write_ha_state()", switch)

    def test_ha_to_tv_switch_and_url_update_same_screensaver_path(self):
        layout = android("HaLayout.java")
        sync = android("ScreensaverSettingsSync.java")
        self.assertIn('SCREENSAVER_URL = "screensaver_url"', layout)
        self.assertIn('SCREENSAVER_HA_AUTH = "screensaver_ha_external_auth"', layout)
        self.assertIn("SCREENSAVER_HA_AUTH.equals(setting)", layout)
        self.assertIn(
            'return prefs.screensaverHaExternalAuth() ? "on" : "off";', layout)
        self.assertIn('if (!"on".equals(value) && !"off".equals(value))', layout)
        self.assertIn("prefs.setScreensaverHaExternalAuth(secureDashboard)", layout)
        self.assertIn("HaLayout.isScreensaver(setting)", sync)
        self.assertIn('prefs.screensaverUrl()', android("OverlayPreferences.java"))
        self.assertIn("HaLayout.apply(local, setting, value)", sync)
        self.assertIn("ScreensaverSettingsEffects.apply(context, setting)", sync)
        self.assertIn("attrs.optString(\"kellie_device_id\"", sync)

    def test_tv_to_ha_switch_edits_are_published_and_off_by_default(self):
        settings = android("ui/settings/ComposeSettingsActivity.kt")
        prefs = android("OverlayPreferences.java")
        client = android("HaAssistClient.java")
        self.assertIn("prefs.setScreensaverHaExternalAuth(enabled)", settings)
        self.assertIn(
            "ComposeSettingsInterop.queueLayoutUpdate(HaLayout.SCREENSAVER_HA_AUTH)",
            settings)
        self.assertIn(
            "ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_URL)",
            settings)
        self.assertIn(
            "return preferences.getBoolean(KEY_SCREENSAVER_HA_AUTH, false)", prefs)
        self.assertIn("HaLayout.wireValue(new OverlayPreferences(context), setting)", client)
        self.assertIn('entity.startsWith("switch.")', client)
        self.assertIn('entity.startsWith("text.")', client)
        self.assertIn("Home Assistant-managed layout", settings)


if __name__ == "__main__":
    unittest.main()
