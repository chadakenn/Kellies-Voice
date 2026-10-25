package com.example.tvvoiceprobe;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Validated, device-local overlay layout and behavior settings.
 * All callers read/write through here so a future approved Home Assistant
 * control channel can use the same validation without exposing credentials.
 * Do not rename existing preference keys: preserve installed TV behavior.
 */
public final class OverlayPreferences {
    public static final String SKIN_GLASS = "glass";
    public static final String SKIN_CLASSIC = "classic";
    public static final String SKIN_SIGNAL = "signal";
    public static final String SKIN_AURORA = "aurora";

    public static final String POSITION_DEFAULT = "default";
    public static final String POSITION_TOP_LEFT = "top_left";
    public static final String POSITION_TOP_RIGHT = "top_right";
    public static final String POSITION_BOTTOM_LEFT = "bottom_left";
    public static final String POSITION_BOTTOM_RIGHT = "bottom_right";
    public static final String POSITION_CENTER = "center";
    public static final String POSITION_BOTTOM_CENTER = "bottom_center";

    public static final String SIZE_SMALL = "small";
    public static final String SIZE_MEDIUM = "medium";
    public static final String SIZE_LARGE = "large";

    public static final String NOISE_QUIET = "quiet";
    public static final String NOISE_NORMAL = "normal";
    public static final String NOISE_TV = "tv";

    private static final String PREFS = "ha_tv_voice_overlay_options";
    private static final String KEY_SKIN = "skin";
    private static final String KEY_AUTO = "auto_listen";
    private static final String KEY_HIDE = "auto_hide";
    private static final String KEY_MAIN_SHORTCUT = "main_launcher_opens_overlay";
    private static final String KEY_POSITION = "overlay_position";
    private static final String KEY_SIZE = "overlay_size";
    private static final String KEY_ALERT_POSITION = "tv_alert_position";
    private static final String KEY_ALERT_SIZE = "tv_alert_size";
    private static final String KEY_HIDE_SECONDS = "overlay_hide_seconds";
    private static final String KEY_NOISE = "room_noise";
    private static final String KEY_DEVICE = "ha_device_id";
    private static final String KEY_REGISTRY_DEVICE = "ha_registry_device_id";
    private static final String KEY_HA_MANAGE = "ha_manage_layout";
    private static final String KEY_TV_EVENTS = "tv_events_enabled";
    private static final String KEY_SPOKEN_ANNOUNCEMENTS = "spoken_announcements";
    private static final String KEY_ANNOUNCEMENT_VOLUME = "spoken_announcement_volume";
    private static final String KEY_FOLLOW = "follow_up_enabled";
    private static final String KEY_FOLLOW_SECONDS = "follow_up_seconds";
    private static final String KEY_WEATHER_POSITION = "weather_position";
    private static final String KEY_WEATHER_SIZE = "weather_size";
    private static final String KEY_WEATHER_SECONDS = "weather_seconds";
    private static final String KEY_CAMERA_MODE = "live_camera_decoder_mode";
    private static final String KEY_ALLOW_UNVERIFIED_MIC = "allow_unverified_mic";
    private static final String KEY_DISABLE_MIC_ON_TV = "disable_microphone_on_this_tv";
    private static final String KEY_UPDATE_CHANNEL = "update_channel";
    private static final String KEY_SCREENSAVER = "screensaver_dashboard";
    private static final String KEY_SCREENSAVER_URL = "screensaver_dashboard_url";
    private static final String KEY_SCREENSAVER_CAMERAS = "screensaver_cameras";
    private static final String KEY_SCREENSAVER_SIZE = "screensaver_size";
    private static final String KEY_SCREENSAVER_DESKTOP = "screensaver_desktop_page";
    private final SharedPreferences preferences;

    public OverlayPreferences(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String skin() {
        String value = preferences.getString(KEY_SKIN, SKIN_GLASS);
        if (SKIN_CLASSIC.equals(value) || SKIN_SIGNAL.equals(value) || SKIN_AURORA.equals(value)) return value;
        return SKIN_GLASS;
    }

    public void setSkin(String value) {
        String stored = SKIN_GLASS;
        if (SKIN_CLASSIC.equals(value)) stored = SKIN_CLASSIC;
        else if (SKIN_SIGNAL.equals(value)) stored = SKIN_SIGNAL;
        else if (SKIN_AURORA.equals(value)) stored = SKIN_AURORA;
        preferences.edit().putString(KEY_SKIN, stored).apply();
    }

    public static String skinLabel(String value) {
        if (SKIN_CLASSIC.equals(value)) return "Compact Charcoal";
        if (SKIN_SIGNAL.equals(value)) return "Night Signal";
        if (SKIN_AURORA.equals(value)) return "Aurora Glass";
        return "Copper Glass";
    }

    public String position() {
        String value = preferences.getString(KEY_POSITION, POSITION_DEFAULT);
        if (POSITION_TOP_LEFT.equals(value) || POSITION_TOP_RIGHT.equals(value)
                || POSITION_BOTTOM_LEFT.equals(value) || POSITION_BOTTOM_RIGHT.equals(value)
                || POSITION_CENTER.equals(value) || POSITION_BOTTOM_CENTER.equals(value)) {
            return value;
        }
        return POSITION_DEFAULT;
    }

    public void setPosition(String value) {
        if (POSITION_TOP_LEFT.equals(value) || POSITION_TOP_RIGHT.equals(value)
                || POSITION_BOTTOM_LEFT.equals(value) || POSITION_BOTTOM_RIGHT.equals(value)
                || POSITION_CENTER.equals(value) || POSITION_BOTTOM_CENTER.equals(value)) {
            preferences.edit().putString(KEY_POSITION, value).apply();
        } else {
            preferences.edit().putString(KEY_POSITION, POSITION_DEFAULT).apply();
        }
    }

    public static String positionLabel(String value) {
        switch (value) {
            case POSITION_TOP_LEFT: return "Top left";
            case POSITION_TOP_RIGHT: return "Top right";
            case POSITION_BOTTOM_LEFT: return "Bottom left";
            case POSITION_BOTTOM_RIGHT: return "Bottom right";
            case POSITION_CENTER: return "Center";
            case POSITION_BOTTOM_CENTER: return "Bottom center";
            default: return "Original location";
        }
    }

    public String size() {
        String value = preferences.getString(KEY_SIZE, SIZE_MEDIUM);
        return SIZE_SMALL.equals(value) || SIZE_LARGE.equals(value) ? value : SIZE_MEDIUM;
    }

    public void setSize(String value) {
        preferences.edit().putString(KEY_SIZE,
                SIZE_SMALL.equals(value) || SIZE_LARGE.equals(value) ? value : SIZE_MEDIUM).apply();
    }

    public static String sizeLabel(String value) {
        switch (value) {
            case SIZE_SMALL: return "Small";
            case SIZE_LARGE: return "Large";
            default: return "Medium (original)";
        }
    }

    public String alertPosition() {
        return TvAlertLayout.position(preferences.getString(KEY_ALERT_POSITION, TvAlertLayout.TOP_RIGHT));
    }

    public void setAlertPosition(String value) {
        String stored = TvAlertLayout.positionOrNull(value);
        preferences.edit().putString(KEY_ALERT_POSITION,
                stored == null ? TvAlertLayout.TOP_RIGHT : stored).apply();
    }

    public String alertSize() {
        return TvAlertLayout.size(preferences.getString(KEY_ALERT_SIZE, TvAlertLayout.MEDIUM));
    }

    public void setAlertSize(String value) {
        String stored = TvAlertLayout.sizeOrNull(value);
        preferences.edit().putString(KEY_ALERT_SIZE,
                stored == null ? TvAlertLayout.MEDIUM : stored).apply();
    }

    // 7 seconds preserves the original approximately 6.5-second delay.
    public int hideSeconds() {
        int value = preferences.getInt(KEY_HIDE_SECONDS, 7);
        return (value == 0 || value == 5 || value == 7 || value == 10 || value == 20)
                ? value : 7;
    }

    public void setHideSeconds(int seconds) {
        if (seconds != 0 && seconds != 5 && seconds != 7
                && seconds != 10 && seconds != 20) return;
        preferences.edit().putInt(KEY_HIDE_SECONDS, seconds)
                .putBoolean(KEY_HIDE, seconds != 0).apply();
    }

    public boolean autoHide() {
        return preferences.getBoolean(KEY_HIDE, true) && hideSeconds() > 0;
    }

    public void setAutoHide(boolean enabled) {
        SharedPreferences.Editor edit = preferences.edit();
        edit.putBoolean(KEY_HIDE, enabled);
        if (enabled && hideSeconds() == 0) edit.putInt(KEY_HIDE_SECONDS, 7);
        edit.apply();
    }

    public String hideLabel() { return autoHide() ? hideSeconds() + " seconds" : "Never"; }

    public boolean autoListen() { return preferences.getBoolean(KEY_AUTO, true); }
    public void setAutoListen(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO, enabled).apply();
    }

    public boolean haManage() {
        return preferences.getBoolean(KEY_HA_MANAGE, false);
    }

    public void setHaManage(boolean enabled) {
        preferences.edit().putBoolean(KEY_HA_MANAGE, enabled).apply();
    }

    /** Local per-TV setting: avoid consuming the HDMI hardware decoder by default. */
    public String cameraMode() {
        return LiveCameraMode.normalize(preferences.getString(KEY_CAMERA_MODE,
                LiveCameraMode.SOFTWARE));
    }

    public void setCameraMode(String mode) {
        preferences.edit().putString(KEY_CAMERA_MODE, LiveCameraMode.normalize(mode)).apply();
    }

    /** Local manual opt-out for Android TVs advertising phantom microphone routes.
     *  This takes priority over compatibility overrides and never auto-enables.
     */
    public boolean microphoneDisabled() {
        return preferences.getBoolean(KEY_DISABLE_MIC_ON_TV, false);
    }

    public void setMicrophoneDisabled(boolean disabled) {
        preferences.edit().putBoolean(KEY_DISABLE_MIC_ON_TV, disabled).apply();
    }

    /** Explicit compatibility override for TVs with unreliable input reports. */
    public boolean allowUnverifiedMic() {
        return preferences.getBoolean(KEY_ALLOW_UNVERIFIED_MIC, false);
    }

    public void setAllowUnverifiedMic(boolean allowed) {
        preferences.edit().putBoolean(KEY_ALLOW_UNVERIFIED_MIC, allowed).apply();
    }

    public boolean tvEvents() {
        return preferences.getBoolean(KEY_TV_EVENTS, false);
    }

    public void setTvEvents(boolean enabled) {
        preferences.edit().putBoolean(KEY_TV_EVENTS, enabled).apply();
    }

    public boolean spokenAnnouncements() {
        return preferences.getBoolean(KEY_SPOKEN_ANNOUNCEMENTS, false);
    }

    public void setSpokenAnnouncements(boolean enabled) {
        preferences.edit().putBoolean(KEY_SPOKEN_ANNOUNCEMENTS, enabled).apply();
    }

    /** Per-utterance spoken TV alert level, stored independently of media volume. */
    public int spokenAnnouncementVolume() {
        return SpeechVolume.normalizePercent(
                preferences.getInt(KEY_ANNOUNCEMENT_VOLUME, SpeechVolume.DEFAULT_PERCENT));
    }

    public void setSpokenAnnouncementVolume(int percent) {
        preferences.edit().putInt(KEY_ANNOUNCEMENT_VOLUME,
                SpeechVolume.normalizePercent(percent)).apply();
    }


    public String deviceId() {
        String id = preferences.getString(KEY_DEVICE, "");
        if (id == null || id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString();
            preferences.edit().putString(KEY_DEVICE, id).apply();
        }
        return id;
    }

    /** Home Assistant device-registry id learned from layout sync. Not the pairing id. */
    public String registryDeviceId() {
        String id = preferences.getString(KEY_REGISTRY_DEVICE, "");
        return RegistryDeviceId.accepted(id);
    }

    public void setRegistryDeviceId(String id) {
        String clean = RegistryDeviceId.accepted(id);
        if (clean.isEmpty()) return;
        preferences.edit().putString(KEY_REGISTRY_DEVICE, clean).apply();
    }

    public static String noiseLabel(String value) {
        if (NOISE_QUIET.equals(value)) return "Quiet room";
        if (NOISE_TV.equals(value)) return "TV on";
        return "Normal";
    }

    public String noise() {
        String value = preferences.getString(KEY_NOISE, NOISE_NORMAL);
        return NOISE_QUIET.equals(value) || NOISE_TV.equals(value) ? value : NOISE_NORMAL;
    }

    public void setNoise(String value) {
        preferences.edit().putString(KEY_NOISE,
                NOISE_QUIET.equals(value) || NOISE_TV.equals(value) ? value : NOISE_NORMAL).apply();
    }

    public boolean followUp() {
        return preferences.getBoolean(KEY_FOLLOW, false);
    }

    public void setFollowUp(boolean enabled) {
        preferences.edit().putBoolean(KEY_FOLLOW, enabled).apply();
    }

    public int followUpSeconds() {
        int value = preferences.getInt(KEY_FOLLOW_SECONDS, FollowUpSession.DEFAULT_SECONDS);
        return value == 5 || value == 10 || value == 15 ? value : FollowUpSession.DEFAULT_SECONDS;
    }

    public void setFollowUpSeconds(int value) {
        preferences.edit().putInt(KEY_FOLLOW_SECONDS,
                value == 5 || value == 10 || value == 15 ? value : FollowUpSession.DEFAULT_SECONDS).apply();
    }

    public boolean mainLauncherOpensOverlay() {
        return preferences.getBoolean(KEY_MAIN_SHORTCUT, false);
    }

    public void setMainLauncherOpensOverlay(boolean enabled) {
        preferences.edit().putBoolean(KEY_MAIN_SHORTCUT, enabled).apply();
    }

    public String weatherPosition() {
        return WeatherLayout.position(preferences.getString(KEY_WEATHER_POSITION, WeatherLayout.BOTTOM_CENTER));
    }

    public void setWeatherPosition(String value) {
        preferences.edit().putString(KEY_WEATHER_POSITION, WeatherLayout.position(value)).apply();
    }

    public String weatherSize() {
        return TvAlertLayout.size(preferences.getString(KEY_WEATHER_SIZE, TvAlertLayout.MEDIUM));
    }

    public void setWeatherSize(String value) {
        preferences.edit().putString(KEY_WEATHER_SIZE, TvAlertLayout.size(value)).apply();
    }

    public int weatherSeconds() {
        int value = preferences.getInt(KEY_WEATHER_SECONDS, 20);
        return value == 8 || value == 30 || value == 45 ? value : 20;
    }

    public void setWeatherSeconds(int value) {
        preferences.edit().putInt(KEY_WEATHER_SECONDS,
                value == 8 || value == 30 || value == 45 ? value : 20).apply();
    }

    /** Stable unless the owner explicitly chooses development. */
    public String updateChannel() {
        return UpdateChannel.normalize(preferences.getString(KEY_UPDATE_CHANNEL, UpdateChannel.STABLE));
    }

    public void setUpdateChannel(String channel) {
        preferences.edit().putString(KEY_UPDATE_CHANNEL, UpdateChannel.normalize(channel)).apply();
    }

    /** Off until the owner asks for a Frigate page while the screensaver is running. */
    public boolean screensaverDashboard() {
        return preferences.getBoolean(KEY_SCREENSAVER, false);
    }

    public void setScreensaverDashboard(boolean enabled) {
        preferences.edit().putBoolean(KEY_SCREENSAVER, enabled).apply();
    }

    public String screensaverUrl() {
        return ScreensaverDashboard.dashboardUrl(preferences.getString(KEY_SCREENSAVER_URL, ""));
    }

    public void setScreensaverUrl(String value) {
        preferences.edit().putString(KEY_SCREENSAVER_URL, ScreensaverDashboard.dashboardUrl(value)).apply();
    }

    public String screensaverCameras() {
        return ScreensaverDashboard.cameras(preferences.getString(KEY_SCREENSAVER_CAMERAS, ""));
    }

    public void setScreensaverCameras(String value) {
        preferences.edit().putString(KEY_SCREENSAVER_CAMERAS, ScreensaverDashboard.cameras(value)).apply();
    }

    /** TV-first default. Switching off restores the original embedded mobile layout. */
    public boolean screensaverDesktopPage() {
        return preferences.getBoolean(KEY_SCREENSAVER_DESKTOP, true);
    }

    public void setScreensaverDesktopPage(boolean desktop) {
        preferences.edit().putBoolean(KEY_SCREENSAVER_DESKTOP, desktop).apply();
    }

    /** Large matches the original full-screen dashboard. */
    public String screensaverSize() {
        String size = ScreensaverDashboard.sizeKey(preferences.getString(KEY_SCREENSAVER_SIZE, "Large"));
        return size.isEmpty() ? "large" : size;
    }

    public void setScreensaverSize(String value) {
        String size = ScreensaverDashboard.sizeKey(value);
        preferences.edit().putString(KEY_SCREENSAVER_SIZE, size.isEmpty() ? "large" : size).apply();
    }
}
