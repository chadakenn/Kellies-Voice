package com.example.tvvoiceprobe;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

/**
 * Rules for the opt-in Frigate page that appears when the TV screensaver starts.
 * No network, no microphone, and no Home Assistant token is accepted in the address.
 */
public final class ScreensaverDashboard {
    public static final int MAX_CAMERAS = 4;
    /** A dream that dies as soon as the dashboard opens should not take the dashboard with it. */
    public static final long EARLY_STOP_MS = 1500L;

    public static String sizeKey(String value) {
        if (value == null) return "";
        switch (value.trim()) {
            case "Small":
            case "small":
                return "small";
            case "Medium":
            case "medium":
                return "medium";
            case "Large":
            case "large":
                return "large";
            default:
                return "";
        }
    }

    public static String sizeLabel(String size) {
        if ("small".equals(size)) return "Small";
        if ("medium".equals(size)) return "Medium";
        return "Large";
    }

    /** Share of the screen the dashboard fills. Large is the whole screen. */
    public static float fraction(String size) {
        if ("small".equals(size)) return 0.48f;
        if ("medium".equals(size)) return 0.72f;
        return 1f;
    }

    private ScreensaverDashboard() { }

    /** Use the installed WebView Chrome version, without advertising an Android/mobile UI. */
    public static String desktopUserAgent(String original) {
        if (original == null) return "";
        Matcher browser = Pattern.compile("Chrome/([0-9]+(?:\\.[0-9]+){1,3})").matcher(original);
        if (!browser.find()) return original;
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/" + browser.group(1) + " Safari/537.36";
    }

    /** Fit more columns/rows without clipping the bottom camera tiles. */
    public static int viewportWidth(String fit) {
        if ("Balanced".equals(fit)) return 1440;
        if ("Larger text".equals(fit)) return 1200;
        return 1920;
    }

    /** Frigate uses a responsive viewport; request a TV-sized CSS viewport for its grid. */
    public static String desktopViewportScript() {
        return desktopViewportScript(1920);
    }

    public static String desktopViewportScript(int width) {
        return "(function(){var m=document.querySelector(\'meta[name=\\\"viewport\\\"]\');" +
                "if(!m){m=document.createElement(\'meta\');m.name=\'viewport\';document.head.appendChild(m);}" +
                "m.setAttribute(\'content\',\'width=" + (width == 1200 || width == 1440 ? width : 1920) + ", initial-scale=1\');})();";
    }

    public static boolean showOverlay(boolean enabled, boolean dreaming, boolean dismissed,
            boolean ourDreamVisible, boolean overlayAllowed, boolean configured) {
        return enabled && dreaming && !dismissed && !ourDreamVisible
                && overlayAllowed && configured;
    }

    public static boolean configured(String url, String cameras) {
        return !dashboardUrl(url).isEmpty() || !cameras(cameras).isEmpty();
    }

    /** Keep the dashboard up when the system dream exits because the dashboard itself opened. */
    public static boolean keepAfterEarlyStop(long shownAtMs, long stoppedAtMs) {
        return shownAtMs > 0 && stoppedAtMs >= shownAtMs
                && stoppedAtMs - shownAtMs < EARLY_STOP_MS;
    }

    /**
     * A Frigate or Home Assistant page on http or https.
     * Blank when the address is missing, too long, or carries a token or password.
     */
    public static String dashboardUrl(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() < 8 || value.length() > 500) return "";
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 32 || c == '\\' || c > 126) return "";
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception ignored) {
            return "";
        }
        String scheme = uri.getScheme();
        if (scheme == null) return "";
        scheme = scheme.toLowerCase(Locale.US);
        if (!"http".equals(scheme) && !"https".equals(scheme)) return "";
        if (uri.getUserInfo() != null || uri.getHost() == null || uri.getHost().isEmpty()) return "";
        if (secretMaterial(uri.getRawQuery()) || secretMaterial(uri.getRawFragment())) return "";
        return value;
    }

    public static boolean sameSite(String page, String next) {
        String allowed = dashboardUrl(page);
        String candidate = dashboardUrl(next);
        if (allowed.isEmpty() || candidate.isEmpty()) return false;
        try {
            URI expected = new URI(allowed);
            URI actual = new URI(candidate);
            String left = expected.getHost();
            String right = actual.getHost();
            int leftPort = expected.getPort();
            int rightPort = actual.getPort();
            return left != null && left.equalsIgnoreCase(right) && leftPort == rightPort
                    && expected.getScheme().equalsIgnoreCase(actual.getScheme());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Up to four camera.* entities. Anything else is dropped. */
    public static String cameras(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        Set<String> kept = new LinkedHashSet<>();
        for (String part : raw.split("[,\\s]+")) {
            String entity = TvEventPayload.cameraEntity(part);
            if (entity == null) continue;
            kept.add(entity);
            if (kept.size() == MAX_CAMERAS) break;
        }
        StringBuilder joined = new StringBuilder();
        for (String entity : kept) {
            if (joined.length() > 0) joined.append(',');
            joined.append(entity);
        }
        return joined.toString();
    }

    public static String[] cameraList(String raw) {
        String clean = cameras(raw);
        return clean.isEmpty() ? new String[0] : clean.split(",");
    }

    private static boolean secretMaterial(String value) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("token") || lower.contains("password")
                || lower.contains("secret") || lower.contains("api_key")
                || lower.contains("apikey") || lower.contains("authorization");
    }
}
