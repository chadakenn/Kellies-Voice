"""Single source of truth for up to four HA snapshot camera dropdowns.

The preexisting screensaver_cameras text entity stays the wire format expected
by Android. Select entities are UI conveniences, not new TV protocol keys.
This module intentionally has no Home Assistant imports so it is easy to test.
"""

from .frigate_settings import validate_frigate_setting


SLOTS = 4
NONE = "None"


class FrigateCameraPicker:
    def __init__(self):
        self._camera_ids = [""] * SLOTS
        self._listeners = {}
        self._text = None
        self._loaded = False

    def bind_text(self, entity):
        self._text = entity

    def unbind_text(self, entity):
        if self._text is entity:
            self._text = None

    @property
    def text_entity(self):
        return self._text

    def load(self, raw):
        """Update dropdowns after the real text entity restores or changes."""
        cameras = validate_frigate_setting("screensaver_cameras", raw)
        values = cameras.split(",") if cameras else []
        self._camera_ids = (values + [""] * SLOTS)[:SLOTS]
        self._loaded = True
        for slot, callback in tuple(self._listeners.items()):
            callback(self._camera_ids[slot])

    def subscribe(self, slot, callback):
        if not 0 <= slot < SLOTS:
            raise ValueError("Invalid camera slot")
        self._listeners[slot] = callback
        if self._loaded:
            callback(self._camera_ids[slot])

    def unsubscribe(self, slot, callback):
        if self._listeners.get(slot) is callback:
            del self._listeners[slot]

    def at(self, slot):
        return self._camera_ids[slot]

    def propose(self, slot, entity_id):
        """Return validated canonical text while preventing duplicate slots."""
        if not 0 <= slot < SLOTS:
            raise ValueError("Invalid camera slot")
        if entity_id:
            validate_frigate_setting("screensaver_cameras", entity_id)
        values = list(self._camera_ids)
        for other in range(SLOTS):
            if other != slot and values[other] == entity_id:
                values[other] = ""
        values[slot] = entity_id
        return validate_frigate_setting("screensaver_cameras", ",".join(v for v in values if v))


def get_camera_picker(hass, entry_id):
    from .const import DOMAIN

    return hass.data.setdefault(DOMAIN, {}).setdefault(
        "frigate_camera_pickers", {}
    ).setdefault(entry_id, FrigateCameraPicker())
