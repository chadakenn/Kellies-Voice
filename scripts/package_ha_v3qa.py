#!/usr/bin/env python3
"""Package ONLY the isolated v3 QA Home Assistant integration."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json
import ast

ROOT = Path(__file__).resolve().parents[1]
PART = Path("custom_components/kellies_voice_v3qa")
SOURCE = ROOT / PART
OUT = ROOT / "dist" / "kellies_voice_v3qa_ha.zip"
assert SOURCE.is_dir(), "Isolated QA component folder missing"
manifest = json.loads((SOURCE / "manifest.json").read_text())
assert manifest["domain"] == "kellies_voice_v3qa"
assert manifest["zeroconf"] == ["_kellies-pair._tcp.local."]
expected = {
    "__init__.py", "config_flow.py", "manifest.json",
    "pairing_key_commitment.py", "pairing_v3_verified.py",
    "pairing_v3_wire.py", "validation.py", "const.py",
    "strings.json", "translations/en.json", "README.md",
    "owner_claim.py", "owner_ws.py", "owner_panel.py",
    "credential_scope.py", "scoped_tv_gate.py",
    "pairing_v3_qa_channel.py", "owner_receipt.py",
    "token_lease_journal.py", "scoped_login_issuer.py",
    "scoped_token_handoff.py", "paired_tv_login.py", "ha_https.py",
    "frontend/kellies-voice-v3qa-owner-panel.js"
}
found = {str(p.relative_to(SOURCE)).replace("\\", "/")
         for p in SOURCE.rglob("*")
         if p.is_file() and "__pycache__" not in p.parts}
assert found == expected, f"Unexpected QA package files: {found ^ expected}"
flow_tree = ast.parse((SOURCE / "config_flow.py").read_text())
assert not any(isinstance(n, ast.Attribute) and n.attr == "async_create_entry"
               for n in ast.walk(flow_tree)), "QA flow must never create HA entries"
assert "async_register_services" not in (SOURCE / "__init__.py").read_text()
OUT.parent.mkdir(exist_ok=True)
with ZipFile(OUT, "w", ZIP_DEFLATED) as archive:
    for relative in sorted(expected):
        archive.write(SOURCE / relative, (PART / relative).as_posix())
print(OUT)
