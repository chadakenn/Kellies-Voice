"""Option text shared with the Android TV app. Do not rename values."""

DOMAIN = "kellies_voice"
QUICK_ACTION_CACHE = "quick_action_entry_cache"
PLATFORMS = ["select", "switch", "text", "binary_sensor", "sensor"]

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
STYLES = [
    "Copper Glass",
    "Compact Charcoal",
    "Night Signal",
    "Aurora Glass",
    "Neon Obsidian",
    "Jade Terminal",
]
HIDE_TIMES = ["Never", "5 seconds", "7 seconds", "10 seconds", "20 seconds"]

SELECTS = (
    ("position", "Overlay position", POSITIONS, "mdi:overscan"),
    ("size", "Overlay size", SIZES, "mdi:relative-scale"),
    ("style", "Overlay style", STYLES, "mdi:palette"),
    ("auto_hide", "Overlay auto-hide", HIDE_TIMES, "mdi:timer-outline"),
)

FRIGATE_SELECTS = (
    ("screensaver_size", "Frigate dashboard size", SIZES, "mdi:resize", "Large"),
    ("screensaver_page_layout", "Frigate page layout", ["Desktop TV", "Automatic"], "mdi:monitor", "Desktop TV"),
    ("screensaver_fit", "Frigate page fit", ["Fit more cameras", "Balanced", "Larger text"], "mdi:fit-to-screen", "Fit more cameras"),
    ("screensaver_live_mode", "HA camera grid playback", ["Snapshots (reliable)", "Live (one camera)", "Live (two cameras)", "Live (four cameras, advanced)"], "mdi:video-wireless", "Snapshots (reliable)"),
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

# Follow-up silence selection only. Cannot turn the microphone on.
FOLLOW_TIMEOUTS = ["5 seconds", "8 seconds", "10 seconds", "15 seconds"]
FOLLOW_SELECTS = (
    ("follow_up_timeout", "Follow-up silence", FOLLOW_TIMEOUTS, "mdi:timer-sand", "8 seconds"),
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

EVENT_ANNOUNCEMENT = "kellies_voice_announcement"
EVENT_CAMERA_POPUP = "kellies_voice_camera_popup"
EVENT_LIVE_CAMERA = "kellies_voice_live_camera"
EVENT_HIDE_CAMERA = "kellies_voice_hide_camera"
EVENT_SHOW_DASHBOARD = "kellies_voice_show_dashboard"
EVENT_HIDE_DASHBOARD = "kellies_voice_hide_dashboard"
EVENT_ACTION_PRESSED = "kellies_voice_announcement_action"
EVENT_FORECAST = "kellies_voice_forecast"
SERVICE_ANNOUNCE = "announce"
SERVICE_SHOW_CAMERA = "show_camera"
SERVICE_SHOW_LIVE_CAMERA = "show_live_camera"
SERVICE_HIDE_CAMERA = "hide_camera"
SERVICE_SHOW_DASHBOARD = "show_dashboard"
SERVICE_HIDE_DASHBOARD = "hide_dashboard"
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
