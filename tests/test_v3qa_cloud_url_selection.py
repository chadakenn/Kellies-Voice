"""Prove HA native URL selection with Cloud enabled and local automatic HTTP."""
from __future__ import annotations
import importlib
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1] / "qa/ha_v3qa/kellies_voice_v3qa"
PKG = "kv_v3qa_cloud_https_tests"
pkg = types.ModuleType(PKG)
pkg.__path__ = [str(ROOT)]
sys.modules[PKG] = pkg
resolver = importlib.import_module(PKG + ".ha_https")

class NoURLAvailableError(ValueError):
    pass


class UrlSelectionTests(unittest.TestCase):
    def setUp(self):
        self.hass = types.SimpleNamespace(cloud="https://tv-example.ui.nabu.casa",
                                          external=None,
                                          internal="http://192.168.1.82:8123")
        network = types.ModuleType("homeassistant.helpers.network")
        network.NoURLAvailableError = NoURLAvailableError
        self.calls = []

        def get_url(hass, **kwargs):
            self.calls.append(kwargs)
            self.assertTrue(kwargs["require_ssl"])
            self.assertTrue(kwargs["prefer_cloud"])
            self.assertTrue(kwargs["prefer_external"])
            if kwargs["allow_external"] and hass.cloud:
                return hass.cloud
            if kwargs["allow_external"] and hass.external:
                return hass.external
            if kwargs["allow_internal"] and hass.internal.startswith("https://"):
                return hass.internal
            raise NoURLAvailableError("No secure URL")
        network.get_url = get_url
        homeassistant = types.ModuleType("homeassistant")
        homeassistant.__path__ = []
        helpers = types.ModuleType("homeassistant.helpers")
        helpers.__path__ = []
        self.p = patch.dict(sys.modules, {
            "homeassistant": homeassistant,
            "homeassistant.helpers": helpers,
            "homeassistant.helpers.network": network,
        })
        self.p.start()
        self.addCleanup(self.p.stop)

    def test_nabu_casa_cloud_selected_when_local_network_is_http(self):
        url, remote = resolver.resolve_tv_login_origin(self.hass)
        self.assertEqual(url, "https://tv-example.ui.nabu.casa")
        self.assertTrue(remote)
        self.assertFalse(self.calls[0]["allow_internal"] is False)
        self.assertFalse(self.calls[1]["allow_internal"])

    def test_manual_https_external_fallback_requires_remote_account(self):
        self.hass.cloud = None
        self.hass.external = "https://ha.example.com"
        self.assertEqual(resolver.resolve_tv_login_origin(self.hass),
                         ("https://ha.example.com", True))

    def test_local_https_remains_local_only_when_no_remote_url(self):
        self.hass.cloud = None
        self.hass.internal = "https://ha.internal.example"
        self.assertEqual(resolver.resolve_tv_login_origin(self.hass),
                         ("https://ha.internal.example", False))

    def test_only_http_does_not_ever_issue_a_credential(self):
        self.hass.cloud = None
        self.hass.external = None
        with self.assertRaises(NoURLAvailableError):
            resolver.resolve_tv_login_origin(self.hass)

    def test_https_url_must_have_no_credentials_path_or_fragment(self):
        for url in ("https://ha.example.com/evil",
                    "https://user:pw@ha.example.com",
                    "https://ha.example.com#frag"):
            self.hass.cloud = url
            with self.subTest(url=url):
                with self.assertRaises(ValueError):
                    resolver.resolve_tv_login_origin(self.hass)


if __name__ == "__main__":
    unittest.main()
