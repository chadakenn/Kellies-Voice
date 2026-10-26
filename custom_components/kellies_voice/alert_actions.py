"""One-use, expiring approvals for explicitly issued TV popup buttons.
No Home Assistant service is executed by this registry or its WebSocket command.
"""
import re
import secrets
import time

_KEY = "button_approvals"
_TOKEN = re.compile(r"^[0-9a-f]{32}$")
_ID = re.compile(r"^[a-z][a-z0-9_]{0,31}$")


class ActionLedger:
    def __init__(self, clock=None):
        self.clock = clock or time.monotonic
        self.active = {}

    def issue(self, device_id, actions, duration):
        self.active.pop(device_id, None)
        if not actions:
            return ""
        token = secrets.token_hex(16)
        self.active[device_id] = (
            token, frozenset(item["id"] for item in actions),
            self.clock() + max(4, min(180, int(duration))),
        )
        return token

    def clear(self, device_id):
        self.active.pop(device_id, None)

    def claim(self, device_id, token, action):
        value = self.active.get(device_id)
        if (value is None or not isinstance(token, str)
                or not _TOKEN.fullmatch(token) or not isinstance(action, str)
                or not _ID.fullmatch(action)):
            return False
        expected, approved, expires = value
        if self.clock() >= expires:
            self.active.pop(device_id, None)
            return False
        if not secrets.compare_digest(token, expected) or action not in approved:
            return False
        self.active.pop(device_id, None)
        return True


def ledger(hass):
    from .const import DOMAIN
    return hass.data.setdefault(DOMAIN, {}).setdefault(_KEY, ActionLedger())
