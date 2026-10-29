"""HA-owned TV home shortcut definitions. Never trust action data from Android.

Only explicitly configured light, scene and script actions can appear on the
full-screen TV. Locks, alarms, covers, arbitrary services and templates are
deliberately excluded. This module has no HA imports for easy security testing.
"""

import re

COUNT = 4
ACTIONS = ("Turn on", "Turn off", "Toggle")
_ENTITY = re.compile(r"(light|scene|script)\.[a-z0-9_]+\Z")
_COMMANDS = {
    "light": {"Turn on": "turn_on", "Turn off": "turn_off", "Toggle": "toggle"},
    "scene": {"Turn on": "turn_on"},
    "script": {"Turn on": "turn_on"},
}


def validate_slot(label, entity_id, action):
    """Validate a single config entry; empty slot has no executable action."""
    if not isinstance(label, str) or not isinstance(entity_id, str) or not isinstance(action, str):
        raise ValueError("Invalid shortcut setting")
    label, entity_id = label.strip(), entity_id.strip()
    if not label and not entity_id:
        return None
    if not label or len(label) > 26 or any(ord(c) < 32 or ord(c) == 127 for c in label):
        raise ValueError("Give each shortcut a visible label of 1-26 characters")
    match = _ENTITY.fullmatch(entity_id)
    if not match:
        raise ValueError("Only light.*, scene.* and script.* entities are allowed")
    domain = match.group(1)
    if action not in _COMMANDS[domain]:
        raise ValueError("This action is not allowed for that entity type")
    return {"label": label, "entity_id": entity_id,
            "domain": domain, "service": _COMMANDS[domain][action]}


def entries_from_options(options):
    """Configured slots only, strict validation even if HA storage was edited."""
    if not isinstance(options, dict):
        return []
    shortcuts = []
    for slot in range(1, COUNT + 1):
        try:
            config = validate_slot(
                options.get(f"shortcut_{slot}_label", ""),
                options.get(f"shortcut_{slot}_entity", ""),
                options.get(f"shortcut_{slot}_action", "Turn on"),
            )
        except ValueError:
            continue  # Invalid restored config can never become an action.
        if config is not None:
            shortcuts.append({"slot": slot, **config})
    return shortcuts


def selection(options, slot):
    """No client-controlled entity_id, domain or service selection."""
    if type(slot) is not int or not 1 <= slot <= COUNT:
        return None
    return next((item for item in entries_from_options(options)
                 if item["slot"] == slot), None)
