package com.example.tvvoiceprobe;

import java.util.Locale;

/**
 * Offline icon renderer for HA Quick Actions.
 *
 * HA supplies a bounded "mdi:name" from entity attributes or an explicit
 * owner-selected override. The TV intentionally never downloads remote SVGs
 * or images, nor accepts drawable URLs from the network. Common MDI families
 * map to an easily recognizable local symbol; unfamiliar icons get a star.
 */
final class TvHomeIcon {
    private TvHomeIcon() { }

    static String glyph(String raw) {
        if (raw == null || raw.length() > 60 || !raw.matches("mdi:[a-z][a-z0-9-]*")) {
            return "✦";
        }
        String name = raw.substring(4).toLowerCase(Locale.ROOT);
        if (has(name, "light", "lamp", "bulb", "led", "sconce", "chandelier",
                "spotlight", "ceiling", "track-light")) return "💡";
        if (has(name, "movie", "film", "theater", "popcorn", "projector")) return "🎬";
        if (has(name, "fan", "air-condition", "weather-windy")) return "🌀";
        // cctv contains "tv", so classify camera before TV.
        if (has(name, "camera", "cctv", "webcam", "video")) return "📷";
        if (has(name, "television", "tv", "monitor", "display", "cast")) return "📺";
        if (has(name, "garage", "car", "vehicle")) return "🚗";
        if (has(name, "home", "house", "door", "sofa")) return "🏠";
        if (has(name, "music", "speaker", "audio", "radio", "playlist")) return "🎵";
        if (has(name, "weather-night", "moon", "sleep", "bed")) return "🌙";
        if (has(name, "weather-sunny", "white-balance-sunny", "sun", "brightness")) return "☀";
        if (has(name, "power", "toggle", "switch", "power-plug")) return "⏻";
        if (has(name, "script", "code", "cog", "wrench", "robot")) return "⚙";
        if (has(name, "clock", "timer", "alarm")) return "⏱";
        if (has(name, "star", "heart", "favorite")) return "★";
        return "✦";
    }

    private static boolean has(String name, String... fragments) {
        for (String fragment : fragments) {
            if (name.contains(fragment)) return true;
        }
        return false;
    }
}
