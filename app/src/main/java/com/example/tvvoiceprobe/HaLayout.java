package com.example.tvvoiceprobe;

/**
 * Maps the Home Assistant layout entities to the on-TV preference setters.
 * Option text must stay in sync with custom_components/kellies_voice/const.py.
 * Unknown values are ignored. Nothing here can start the microphone.
 */
public final class HaLayout {
    public static final String POSITION = "position";
    public static final String SIZE = "size";
    public static final String STYLE = "style";
    public static final String AUTO_HIDE = "auto_hide";
    public static final String AUTO_LISTEN = "auto_listen";
    public static final String ALERT_POSITION = "alert_position";
    public static final String ALERT_SIZE = "alert_size";
    public static final String NOISE = "noise";
    public static final String FOLLOW_TIMEOUT = "follow_up_timeout";
    public static final String WEATHER_POSITION = "weather_position";
    public static final String WEATHER_SIZE = "weather_size";
    public static final String WEATHER_DURATION = "weather_duration";

    private HaLayout() {}

    public static boolean known(String setting) {
        return POSITION.equals(setting) || SIZE.equals(setting) || STYLE.equals(setting)
                || AUTO_HIDE.equals(setting) || AUTO_LISTEN.equals(setting)
                || ALERT_POSITION.equals(setting) || ALERT_SIZE.equals(setting)
                || NOISE.equals(setting) || FOLLOW_TIMEOUT.equals(setting)
                || WEATHER_POSITION.equals(setting) || WEATHER_SIZE.equals(setting)
                || WEATHER_DURATION.equals(setting);
    }

    public static boolean changesPanel(String setting) {
        return POSITION.equals(setting) || SIZE.equals(setting) || STYLE.equals(setting);
    }

    /** Value to write into Home Assistant for the TV's current preference. */
    public static String wireValue(OverlayPreferences prefs, String setting) {
        switch (setting) {
            case POSITION:
                return OverlayPreferences.positionLabel(prefs.position());
            case SIZE:
                if (OverlayPreferences.SIZE_SMALL.equals(prefs.size())) return "Small";
                if (OverlayPreferences.SIZE_LARGE.equals(prefs.size())) return "Large";
                return "Medium";
            case STYLE:
                return OverlayPreferences.skinLabel(prefs.skin());
            case AUTO_HIDE:
                return prefs.autoHide() ? prefs.hideSeconds() + " seconds" : "Never";
            case AUTO_LISTEN:
                return prefs.autoListen() ? "on" : "off";
            case ALERT_POSITION:
                return TvAlertLayout.positionLabel(prefs.alertPosition());
            case ALERT_SIZE:
                return TvAlertLayout.sizeLabel(prefs.alertSize());
            case NOISE:
                return MicSensitivity.label(prefs.noise());
            case FOLLOW_TIMEOUT:
                return FollowUpSession.label(prefs.followUpSeconds());
            case WEATHER_POSITION:
                return WeatherLayout.positionLabel(prefs.weatherPosition());
            case WEATHER_SIZE:
                return TvAlertLayout.sizeLabel(prefs.weatherSize());
            case WEATHER_DURATION:
                return WeatherLayout.durationLabel(prefs.weatherSeconds());
            default:
                return null;
        }
    }

    /** @return true when a stored preference actually changed. */
    public static boolean apply(OverlayPreferences prefs, String setting, String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        switch (setting) {
            case POSITION:
                String position = positionKey(value);
                if (position == null || position.equals(prefs.position())) return false;
                prefs.setPosition(position);
                return true;
            case SIZE:
                String size = sizeKey(value);
                if (size == null || size.equals(prefs.size())) return false;
                prefs.setSize(size);
                return true;
            case STYLE:
                String skin = styleKey(value);
                if (skin == null || skin.equals(prefs.skin())) return false;
                prefs.setSkin(skin);
                return true;
            case AUTO_HIDE:
                Integer seconds = hideSeconds(value);
                if (seconds == null) return false;
                if (seconds == 0 && !prefs.autoHide()) return false;
                if (seconds > 0 && prefs.autoHide() && seconds == prefs.hideSeconds()) return false;
                prefs.setHideSeconds(seconds);
                return true;
            case AUTO_LISTEN:
                if (!"on".equals(value) && !"off".equals(value)) return false;
                boolean enabled = "on".equals(value);
                if (enabled == prefs.autoListen()) return false;
                prefs.setAutoListen(enabled);
                return true;
            case ALERT_POSITION:
                String alertPosition = TvAlertLayout.positionOrNull(value);
                if (alertPosition == null || alertPosition.equals(prefs.alertPosition())) return false;
                prefs.setAlertPosition(alertPosition);
                return true;
            case ALERT_SIZE:
                String alertSize = TvAlertLayout.sizeOrNull(value);
                if (alertSize == null || alertSize.equals(prefs.alertSize())) return false;
                prefs.setAlertSize(alertSize);
                return true;
            case NOISE:
                String noise = MicSensitivity.keyOrNull(value);
                if (noise == null || noise.equals(prefs.noise())) return false;
                prefs.setNoise(noise);
                return true;
            case FOLLOW_TIMEOUT:
                if (!FollowUpSession.remoteTimeoutAllowed(prefs.followUp())) return false;
                int followSeconds = FollowUpSession.seconds(value);
                if (followSeconds < 0 || followSeconds == prefs.followUpSeconds()) return false;
                prefs.setFollowUpSeconds(followSeconds);
                return true;
            case WEATHER_POSITION:
                String weatherPosition = WeatherLayout.positionOrNull(value);
                if (weatherPosition == null || weatherPosition.equals(prefs.weatherPosition())) return false;
                prefs.setWeatherPosition(weatherPosition);
                return true;
            case WEATHER_SIZE:
                String weatherSize = TvAlertLayout.sizeOrNull(value);
                if (weatherSize == null || weatherSize.equals(prefs.weatherSize())) return false;
                prefs.setWeatherSize(weatherSize);
                return true;
            case WEATHER_DURATION:
                int weatherSeconds = WeatherLayout.durationSeconds(value);
                if (weatherSeconds < 0 || weatherSeconds == prefs.weatherSeconds()) return false;
                prefs.setWeatherSeconds(weatherSeconds);
                return true;
            default:
                return false;
        }
    }

    private static String positionKey(String value) {
        switch (value) {
            case "Original location":
            case OverlayPreferences.POSITION_DEFAULT:
                return OverlayPreferences.POSITION_DEFAULT;
            case "Top left":
            case OverlayPreferences.POSITION_TOP_LEFT:
                return OverlayPreferences.POSITION_TOP_LEFT;
            case "Top right":
            case OverlayPreferences.POSITION_TOP_RIGHT:
                return OverlayPreferences.POSITION_TOP_RIGHT;
            case "Bottom left":
            case OverlayPreferences.POSITION_BOTTOM_LEFT:
                return OverlayPreferences.POSITION_BOTTOM_LEFT;
            case "Bottom right":
            case OverlayPreferences.POSITION_BOTTOM_RIGHT:
                return OverlayPreferences.POSITION_BOTTOM_RIGHT;
            case "Center":
            case OverlayPreferences.POSITION_CENTER:
                return OverlayPreferences.POSITION_CENTER;
            case "Bottom center":
            case OverlayPreferences.POSITION_BOTTOM_CENTER:
                return OverlayPreferences.POSITION_BOTTOM_CENTER;
            default:
                return null;
        }
    }

    private static String sizeKey(String value) {
        switch (value) {
            case "Small":
            case OverlayPreferences.SIZE_SMALL:
                return OverlayPreferences.SIZE_SMALL;
            case "Medium":
            case "Medium (original)":
            case OverlayPreferences.SIZE_MEDIUM:
                return OverlayPreferences.SIZE_MEDIUM;
            case "Large":
            case OverlayPreferences.SIZE_LARGE:
                return OverlayPreferences.SIZE_LARGE;
            default:
                return null;
        }
    }

    private static String styleKey(String value) {
        if ("Aurora Glass".equals(value)) return OverlayPreferences.SKIN_AURORA;
        switch (value) {
            case "Copper Glass":
            case OverlayPreferences.SKIN_GLASS:
                return OverlayPreferences.SKIN_GLASS;
            case "Compact Charcoal":
            case OverlayPreferences.SKIN_CLASSIC:
                return OverlayPreferences.SKIN_CLASSIC;
            case "Night Signal":
            case OverlayPreferences.SKIN_SIGNAL:
                return OverlayPreferences.SKIN_SIGNAL;
            default:
                return null;
        }
    }

    private static Integer hideSeconds(String value) {
        switch (value) {
            case "Never":
            case "0":
                return 0;
            case "5 seconds":
            case "5":
                return 5;
            case "7 seconds":
            case "7":
                return 7;
            case "10 seconds":
            case "10":
                return 10;
            case "20 seconds":
            case "20":
                return 20;
            default:
                return null;
        }
    }
}
