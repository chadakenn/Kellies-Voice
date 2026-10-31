"""Small, safe Home Assistant icon contract for the Android TV buttons.

Only MDI names (never URLs, HTML, arbitrary image content or SVG) leave HA.
The TV uses its local glyph map. Unknown MDI names get a category fallback.
"""
import re

ICON_CHOICES = (
    "Auto", "Lightbulb", "Movie", "Home", "Fan", "Camera",
    "Star", "Script", "Power", "Music", "Sun", "Moon", "TV", "Garage",
)
ICON_OVERRIDES = {
    "Lightbulb": "mdi:lightbulb",
    "Movie": "mdi:movie-open",
    "Home": "mdi:home",
    "Fan": "mdi:fan",
    "Camera": "mdi:camera",
    "Star": "mdi:star",
    "Script": "mdi:script-text",
    "Power": "mdi:power",
    "Music": "mdi:music",
    "Sun": "mdi:white-balance-sunny",
    "Moon": "mdi:weather-night",
    "TV": "mdi:television",
    "Garage": "mdi:garage",
}
DOMAIN_FALLBACK = {
    "light": "mdi:lightbulb",
    "scene": "mdi:movie-open",
    "script": "mdi:script-text",
}
_MDI = re.compile(r"mdi:[a-z][a-z0-9-]{0,55}\Z")


def validate_override(value):
    """Reject forged, oversized or unrecognized overrides before saving."""
    if not isinstance(value, str) or value not in ICON_CHOICES:
        raise ValueError("Choose an available Quick Action icon")
    return value


def resolve_shortcut_icon(override, entity_icon, domain):
    """Prefer user override, otherwise the actual HA icon, then domain default.

    This intentionally never sends target entity IDs to the TV.
    """
    fallback = DOMAIN_FALLBACK.get(domain, "mdi:star")
    if override != "Auto":
        return ICON_OVERRIDES.get(override, fallback)
    if isinstance(entity_icon, str) and _MDI.fullmatch(entity_icon):
        return entity_icon
    return fallback
