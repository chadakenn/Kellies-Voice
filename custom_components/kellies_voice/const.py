"""Option text shared with the Android TV app. Do not rename values."""

DOMAIN = "kellies_voice"
PLATFORMS = ["select", "switch", "binary_sensor", "text"]

POSITIONS = [
    "Original location",
    "Top left",
    "Top right",
    "Bottom left",
    "Bottom right",
    "Center",
    "Bottom center",
]
SIZES = ["Small", "Medium", "Large"]
STYLES = ["Copper Glass", "Compact Charcoal", "Night Signal", "Aurora Glass"]
HIDE_TIMES = ["Never", "5 seconds", "7 seconds", "10 seconds", "20 seconds"]

SELECTS = (
    ("position", "Overlay position", POSITIONS, "mdi:overscan"),
    ("size", "Overlay size", SIZES, "mdi:relative-scale"),
    ("style", "Overlay style", STYLES, "mdi:palette"),
    ("auto_hide", "Overlay auto-hide", HIDE_TIMES, "mdi:timer-outline"),
)

# Separate from the voice overlay. Top right and Medium match the TV's current popup.
ALERT_POSITIONS = [
    "Top left",
    "Top right",
    "Bottom left",
    "Bottom right",
    "Center",
]
ALERT_SIZES = ["Small", "Medium", "Large"]
ALERT_SELECTS = (
    ("alert_position", "TV alert position", ALERT_POSITIONS, "mdi:picture-in-picture-top-right", "Top right"),
    ("alert_size", "TV alert size", ALERT_SIZES, "mdi:relative-scale", "Medium"),
)

# Pause detector only. Normal matches a TV that has never changed this setting.
NOISE_PRESETS = ["Quiet room", "Normal", "TV on"]
NOISE_SELECTS = (
    ("noise", "Microphone sensitivity", NOISE_PRESETS, "mdi:microphone-settings", "Normal"),
)

# Forecast card only. These do not move the voice overlay or camera popups.
WEATHER_POSITIONS = [
    "Top left",
    "Top right",
    "Bottom left",
    "Bottom right",
    "Bottom center",
    "Center",
]
WEATHER_DURATIONS = ["8 seconds", "20 seconds", "30 seconds", "45 seconds"]
WEATHER_SELECTS = (
    ("weather_position", "Forecast position", WEATHER_POSITIONS, "mdi:weather-partly-cloudy", "Bottom center"),
    ("weather_size", "Forecast size", ALERT_SIZES, "mdi:relative-scale", "Medium"),
    ("weather_duration", "Forecast duration", WEATHER_DURATIONS, "mdi:timer-outline", "20 seconds"),
)

# Screensaver dashboard only. Large matches a TV that has never changed this.
SCREENSAVER_SIZES = ["Small", "Medium", "Large"]
SCREENSAVER_SELECTS = (
    ("screensaver_size", "Screensaver size", SCREENSAVER_SIZES, "mdi:relative-scale", "Large"),
)
SCREENSAVER_CAMERAS = "screensaver_cameras"
SCREENSAVER_CAMERAS_EMPTY = "none"

EVENT_ANNOUNCEMENT = "kellies_voice_announcement"
EVENT_CAMERA_POPUP = "kellies_voice_camera_popup"
EVENT_LIVE_CAMERA = "kellies_voice_live_camera"
EVENT_HIDE_CAMERA = "kellies_voice_hide_camera"
EVENT_FORECAST = "kellies_voice_forecast"
SERVICE_ANNOUNCE = "announce"
SERVICE_SHOW_CAMERA = "show_camera"
SERVICE_SHOW_LIVE_CAMERA = "show_live_camera"
SERVICE_HIDE_CAMERA = "hide_camera"
SERVICE_SHOW_FORECAST = "show_forecast"

MAX_TITLE_LENGTH = 65
MAX_TEXT_LENGTH = 500
MIN_DURATION_SECONDS = 4
MAX_DURATION_SECONDS = 45
DEFAULT_ANNOUNCE_SECONDS = 10
DEFAULT_CAMERA_SECONDS = 20

DEFAULT_LIVE_SECONDS = 90
MIN_LIVE_SECONDS = 4
MAX_LIVE_SECONDS = 180
