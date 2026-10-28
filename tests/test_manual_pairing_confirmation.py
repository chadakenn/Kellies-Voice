"""Manual pairing must require a second ID comparison before creating an HA entry.

Runs without Home Assistant installed. Test the actual flow method AST, not a
separate reimplementation, with small fakes for config-flow base methods.
"""
import ast
import json
import pathlib
import unittest
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
tree = ast.parse((ROOT / "config_flow.py").read_text(encoding="utf-8"))
flow = next(n for n in tree.body if isinstance(n, ast.ClassDef)
            and n.name == "KellieVoiceConfigFlow")
methods = [n for n in flow.body if isinstance(n, ast.AsyncFunctionDef)
           and n.name in {"async_step_user", "async_step_manual_confirm"}]
assert len(methods) == 2

class Vol:
    @staticmethod
    def Schema(x): return x
    @staticmethod
    def Required(name): return name
    @staticmethod
    def Optional(name, default=None): return name

def clean_tv_device_id(value):
    try:
        parsed = uuid.UUID(str(value).strip())
        if parsed.version != 4: raise ValueError()
        return str(parsed)
    except (ValueError, AttributeError):
        raise ValueError("Invalid TV pairing ID.") from None

namespace = {"vol": Vol, "clean_tv_device_id": clean_tv_device_id}
exec(compile(ast.Module(body=methods, type_ignores=[]), "config_flow_methods", "exec"), namespace)

class FakeFlow:
    async_step_user = namespace["async_step_user"]
    async_step_manual_confirm = namespace["async_step_manual_confirm"]

    def __init__(self):
        self._manual_device_id = ""
        self._manual_name = ""
        self.created = []
        self.unique_ids = []

    async def async_set_unique_id(self, value):
        self.unique_ids.append(value)

    def _abort_if_unique_id_configured(self):
        pass

    def async_show_form(self, **kwargs):
        return {"type": "form", **kwargs}

    def async_create_entry(self, **kwargs):
        self.created.append(kwargs)
        return {"type": "create_entry", **kwargs}


class ManualConfirmationTests(unittest.IsolatedAsyncioTestCase):
    async def test_valid_manual_id_shows_confirmation_without_creating(self):
        flow = FakeFlow()
        tv_id = str(uuid.uuid4())
        first = await flow.async_step_user({"device_id": tv_id.upper(), "name": "Garage"})
        self.assertEqual("form", first["type"])
        self.assertEqual("manual_confirm", first["step_id"])
        self.assertEqual(tv_id, first["description_placeholders"]["pairing_id"])
        self.assertEqual("Garage", first["description_placeholders"]["name"])
        self.assertEqual([], flow.created)
        self.assertEqual([], flow.unique_ids)
        # The user must copy the final four characters from the ID ON THE TV.
        self.assertIn("confirmation_suffix", first["data_schema"])
        last = await flow.async_step_manual_confirm({
            "confirmation_suffix": tv_id[-4:].upper(),
        })
        self.assertEqual("create_entry", last["type"])
        self.assertEqual(tv_id, last["data"]["device_id"])
        self.assertEqual("Garage", last["title"])
        self.assertEqual([tv_id], flow.unique_ids)
        self.assertEqual(1, len(flow.created))

    async def test_wrong_or_missing_last_four_cannot_create_pairing(self):
        flow = FakeFlow()
        tv_id = str(uuid.uuid4())
        await flow.async_step_user({"device_id": tv_id, "name": "Garage"})
        for typed in ("", "0000" if tv_id[-4:] != "0000" else "ffff", "oops"):
            result = await flow.async_step_manual_confirm({
                "confirmation_suffix": typed,
            })
            self.assertEqual("form", result["type"])
            self.assertEqual("pairing_suffix_mismatch",
                             result["errors"]["confirmation_suffix"])
            self.assertEqual([], flow.created)
            self.assertEqual([], flow.unique_ids)
        accepted = await flow.async_step_manual_confirm({
            "confirmation_suffix": tv_id[-4:],
        })
        self.assertEqual("create_entry", accepted["type"])

    async def test_reported_valid_uuid_typo_is_blocked_by_actual_tv_suffix(self):
        # Reporter #69 used a valid-looking UUID differing by one character
        # near the end. Formats alone cannot distinguish two distinct TVs.
        actual_on_tv = str(uuid.uuid4())
        wrong_suffix = (
            "0000" if actual_on_tv[-4:] != "0000" else "ffff"
        )
        mistyped = actual_on_tv[:-4] + wrong_suffix
        self.assertEqual(4, uuid.UUID(mistyped).version)

        flow = FakeFlow()
        await flow.async_step_user({"device_id": mistyped, "name": "Second TV"})
        rejected = await flow.async_step_manual_confirm({
            "confirmation_suffix": actual_on_tv[-4:],
        })
        self.assertEqual("manual_confirm", rejected["step_id"])
        self.assertEqual("pairing_suffix_mismatch",
                         rejected["errors"]["confirmation_suffix"])
        self.assertEqual([], flow.created)

        # Returning to the first step allows the user to enter the real ID.
        corrected = await flow.async_step_user({
            "device_id": actual_on_tv, "name": "Second TV",
        })
        self.assertEqual("manual_confirm", corrected["step_id"])
        accepted = await flow.async_step_manual_confirm({
            "confirmation_suffix": actual_on_tv[-4:],
        })
        self.assertEqual("create_entry", accepted["type"])
        self.assertEqual(actual_on_tv, accepted["data"]["device_id"])

    async def test_invalid_manual_id_does_not_advance(self):
        flow = FakeFlow()
        form = await flow.async_step_user({"device_id": "wrong-id", "name": "TV"})
        self.assertEqual("user", form["step_id"])
        self.assertEqual("invalid_device_id", form["errors"]["device_id"])
        self.assertEqual([], flow.created)

    async def test_uninitialized_confirmation_does_not_create(self):
        flow = FakeFlow()
        view = await flow.async_step_manual_confirm({})
        self.assertEqual("user", view["step_id"])
        self.assertEqual([], flow.created)

    async def test_long_name_does_not_advance(self):
        flow = FakeFlow()
        form = await flow.async_step_user({"device_id": str(uuid.uuid4()), "name": "X" * 65})
        self.assertEqual("invalid_name", form["errors"]["name"])
        self.assertEqual([], flow.created)

    def test_text_of_confirmation_is_present_in_shipped_translations(self):
        strings = json.loads((ROOT / "strings.json").read_text(encoding="utf-8"))
        english = json.loads((ROOT / "translations" / "en.json").read_text(encoding="utf-8"))
        self.assertEqual(strings, english)
        description = english["config"]["step"]["manual_confirm"]["description"]
        self.assertIn("{pairing_id}", description)
        self.assertIn("cannot verify", description)
        self.assertIn("Back", description)
        self.assertIn("LAST FOUR", description)
        self.assertEqual("Last four characters on TV",
                         english["config"]["step"]["manual_confirm"]["data"]["confirmation_suffix"])
        self.assertIn("pairing_suffix_mismatch", english["config"]["error"])


if __name__ == "__main__":
    unittest.main()
