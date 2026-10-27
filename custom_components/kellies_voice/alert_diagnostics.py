"""Session-scoped, temporary receipts for HA-to-TV popup diagnostics.

In-memory only. Never stores announcement text, camera IDs, URLs, tokens,
pairing identifiers in HA states/logs, or a claim that pixels reached HDMI.
"""
from datetime import datetime, timezone
import re
import secrets
import time

from .const import DOMAIN

_KEY = "tv_alert_diagnostics"
_VERSION = re.compile(r"^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}(?:-dev\.[0-9]{1,4})?(?:-test)?$")
_KINDS = {
    "kellies_voice_announcement": "announcement",
    "kellies_voice_camera_popup": "snapshot",
    "kellies_voice_live_camera": "live camera",
    "kellies_voice_forecast": "forecast",
}
_TTL_SECONDS = 120
_MAX_PENDING = 16


def _now():
    return datetime.now(timezone.utc).isoformat()


class TvDiagnosticTracker:
    def __init__(self, clock=None):
        self._clock = clock or time.monotonic
        self._sessions = {}  # TV ID -> opaque session token -> (socket, pending)
        self._states = {}  # TV ID -> bounded, non-sensitive aggregates

    def subscribe(self, tv, session, socket):
        self._sessions.setdefault(tv, {})[session] = (socket, {})

    def unsubscribe(self, tv, session):
        sessions = self._sessions.get(tv)
        if sessions:
            sessions.pop(session, None)
            if not sessions:
                self._sessions.pop(tv, None)

    def active(self, tv, socket):
        return any(conn is socket for conn, _ in self._sessions.get(tv, {}).values())

    def issue(self, tv, session, kind):
        if kind not in _KINDS:
            return None
        item = self._sessions.get(tv, {}).get(session)
        if item is None:
            return None
        _, pending = item
        now = self._clock()
        for receipt, entry in tuple(pending.items()):
            if now - entry[1] >= _TTL_SECONDS:
                pending.pop(receipt, None)
        while len(pending) >= _MAX_PENDING:
            oldest = next(iter(pending))
            pending.pop(oldest, None)
        receipt = secrets.token_hex(12)
        pending[receipt] = [kind, now, False]
        return receipt

    def report(self, tv, socket, stage, *, receipt="", version="", code=0):
        """Accept reports only from an active authenticated TV subscription."""
        sessions = self._sessions.get(tv, {})
        matching = [pending for conn, pending in sessions.values() if conn is socket]
        if not matching:
            return False
        if stage == "hello":
            if (not isinstance(version, str) or not _VERSION.fullmatch(version)
                    or type(code) is not int or not 1 <= code <= 1000000000):
                return False
            row = self._states.setdefault(tv, {})
            row["app_version"] = version
            row["app_code"] = code
            row["reported_at"] = _now()
            return True
        if stage not in ("received", "popup_created"):
            return False
        if not isinstance(receipt, str) or len(receipt) != 24:
            return False
        now = self._clock()
        for pending in matching:
            item = pending.get(receipt)
            if item is None:
                continue
            kind, issued, received = item
            if now - issued >= _TTL_SECONDS:
                pending.pop(receipt, None)
                return False
            row = self._states.setdefault(tv, {})
            if stage == "received":
                if received:
                    return False
                item[2] = True
                row["last_received_at"] = _now()
                row["last_kind"] = _KINDS[kind]
                row["last_result"] = "Received by TV"
                return True
            if not received:  # Don't claim popup creation without a received receipt.
                return False
            pending.pop(receipt, None)
            row["last_popup_created_at"] = _now()
            row["last_kind"] = _KINDS[kind]
            row["last_result"] = "Popup created on TV"
            return True
        return False

    def snapshot(self, tv):
        return dict(self._states.get(tv, {}))


def diagnostics(hass):
    return hass.data.setdefault(DOMAIN, {}).setdefault(_KEY, TvDiagnosticTracker())
