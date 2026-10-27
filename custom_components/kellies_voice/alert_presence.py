"""Runtime status of authenticated per-TV alert WebSocket subscriptions.

'Connected' describes a live alert-subscription socket, NOT a promise that
Android displayed a notification. No tokens or private alert data are stored.
"""

from datetime import datetime, timezone

from homeassistant.helpers.dispatcher import async_dispatcher_send

from .const import DOMAIN

_KEY = "alert_presence"


class PresenceTracker:
    """Tracks active authenticated subscriptions without persisting secrets."""

    def __init__(self):
        self.clients = {}
        self.seen = {}

    def connected(self, device_id: str, client_key: object) -> bool:
        previous = self.online(device_id)
        self.clients.setdefault(device_id, set()).add(client_key)
        self.seen[device_id] = datetime.now(timezone.utc).isoformat()
        return not previous

    def disconnected(self, device_id: str, client_key: object) -> bool:
        clients = self.clients.get(device_id)
        if not clients or client_key not in clients:
            return False
        clients.discard(client_key)
        if not clients:
            self.clients.pop(device_id, None)
            return True
        return False

    def online(self, device_id: str) -> bool:
        return bool(self.clients.get(device_id))

    def last_seen(self, device_id: str):
        return self.seen.get(device_id)


def tracker(hass):
    return hass.data.setdefault(DOMAIN, {}).setdefault(_KEY, PresenceTracker())


def signal(device_id):
    return f"{DOMAIN}_alert_presence_{device_id}"


def receiver_connected(hass, device_id, client_key):
    present = tracker(hass)
    present.connected(device_id, client_key)
    async_dispatcher_send(hass, signal(device_id))


def receiver_disconnected(hass, device_id, client_key):
    present = tracker(hass)
    if present.disconnected(device_id, client_key):
        async_dispatcher_send(hass, signal(device_id))
