"""Validate Frigate controls without importing Home Assistant."""

from urllib.parse import urlsplit
import re


_CAMERA = re.compile(r"camera\.[a-z0-9_]+\Z")
_SECRET = ("token", "password", "secret", "api_key", "apikey", "authorization")


def validate_frigate_setting(setting: str, value: str) -> str:
    if not isinstance(value, str):
        raise ValueError("Value must be text.")
    value = value.strip()
    if len(value) > 255:
        raise ValueError("Frigate setting is too long.")
    if setting == "screensaver_url":
        if not value:
            return ""
        if any(ord(c) <= 32 or ord(c) > 126 or c == "\\" for c in value):
            raise ValueError("Invalid Frigate address.")
        try:
            uri = urlsplit(value)
            if uri.scheme not in ("http", "https") or not uri.hostname or uri.username or uri.password:
                raise ValueError("Use a full HTTP(S) Frigate URL without credentials.")
            if any(word in (uri.query + uri.fragment).lower() for word in _SECRET):
                raise ValueError("Never put an access token or password into the URL.")
            _ = uri.port
        except ValueError:
            raise ValueError("Invalid HTTP(S) address.") from None
        return value
    if setting == "screensaver_cameras":
        if not value:
            return ""
        ids = re.split(r"[,\s]+", value)
        if len(ids) > 4 or not all(_CAMERA.fullmatch(entity) for entity in ids):
            raise ValueError("Enter up to four comma-separated camera.* entity IDs.")
        return ",".join(dict.fromkeys(ids))
    raise ValueError("Unknown Frigate setting.")
