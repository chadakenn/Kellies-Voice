package com.example.tvvoiceprobe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chooses a public GitHub release for the TV updater.
 * Stable is the default. Development is opt-in and only accepts a published
 * prerelease. No token, Actions artifact, or private URL is ever selected.
 *
 * JSON is read here instead of {@code org.json}. Android's unit-test
 * android.jar stubs that package, and the allowlist has to run on the host.
 */
public final class UpdateChannel {
    public static final String STABLE = "stable";
    public static final String DEVELOPMENT = "development";
    public static final String OFFER = "offer";
    public static final String CURRENT = "current";
    public static final String ROLLBACK_BLOCKED = "rollback-blocked";
    public static final String LATEST =
            "https://api.github.com/repos/chadakenn/Kellies-Voice/releases/latest";
    public static final String DEV_FEED =
            "https://api.github.com/repos/chadakenn/Kellies-Voice/releases?per_page=20";

    private UpdateChannel() { }

    public static String normalize(String raw) {
        return DEVELOPMENT.equals(raw) ? DEVELOPMENT : STABLE;
    }

    /** Daily checks stay on the public stable release. Development is manual. */
    public static boolean allowsAutomaticCheck(String channel) {
        return STABLE.equals(normalize(channel));
    }

    public static String feedUrl(String channel) {
        return DEVELOPMENT.equals(normalize(channel)) ? DEV_FEED : LATEST;
    }

    public static boolean devTag(String tag) {
        return tag != null && tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+-dev\\.[0-9]+");
    }

    public static final class Selected {
        public final String tag;
        public final String notes;
        public final String apkUrl;
        public final String metadataUrl;
        public final long size;

        Selected(String tag, String notes, String apkUrl, String metadataUrl, long size) {
            this.tag = tag;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.metadataUrl = metadataUrl;
            this.size = size;
        }
    }

    public static final class Meta {
        public final int versionCode;
        public final String versionName;
        public final String sha256;

        Meta(int versionCode, String versionName, String sha256) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.sha256 = sha256;
        }
    }

    public static Selected select(String channel, String payload) {
        Object parsed = readFeed(payload);
        if (DEVELOPMENT.equals(normalize(channel))) return selectDevelopment(parsed);
        return selectStable(parsed);
    }

    public static Meta requireMetadata(String channel, String tag, String payload) {
        if (tag == null) throw new IllegalArgumentException("Invalid release integrity metadata.");
        Object parsed = readDocument(payload, 16384, "Invalid release integrity metadata.");
        Map<String, Object> info = asMap(parsed);
        if (info == null) throw new IllegalArgumentException("Invalid release integrity metadata.");
        String metaTag = text(info, "tag");
        String name = text(info, "versionName");
        String hash = text(info, "sha256").toLowerCase(Locale.US);
        long code = whole(info.get("versionCode"));
        if (!tag.equals(metaTag) || !hash.matches("[a-f0-9]{64}") || code <= 0 || code > 1_000_000L) {
            throw new IllegalArgumentException("Invalid release integrity metadata.");
        }
        if (DEVELOPMENT.equals(normalize(channel))) {
            if (!devTag(tag) || !tag.equals("v" + name)) {
                throw new IllegalArgumentException("Invalid release integrity metadata.");
            }
        } else if (!ReleaseAssets.stableTag(tag) || name.isEmpty() || name.contains("-dev")) {
            throw new IllegalArgumentException("Invalid release integrity metadata.");
        }
        return new Meta((int) code, name, hash);
    }

    /**
     * @return {@link #OFFER} only when the published code is newer than the
     *         installed app. Stable cannot replace a newer development build.
     */
    public static String verdict(String channel, long offeredCode, long installedCode, String installedName) {
        if (offeredCode > installedCode) return OFFER;
        boolean installedDev = installedName != null && installedName.contains("-dev");
        if (STABLE.equals(normalize(channel)) && installedDev) return ROLLBACK_BLOCKED;
        return CURRENT;
    }

    private static Object readFeed(String payload) {
        return readDocument(payload, 750000, "Update metadata was empty.");
    }

    private static Object readDocument(String payload, int maxChars, String emptyMessage) {
        if (payload == null) throw new IllegalArgumentException(emptyMessage);
        String body = payload.trim();
        if (body.startsWith("\uFEFF")) body = body.substring(1).trim();
        if (body.isEmpty() || body.length() > maxChars) {
            throw new IllegalArgumentException(emptyMessage);
        }
        try {
            return Json.read(body);
        } catch (Json.Syntax error) {
            throw new IllegalArgumentException(
                    emptyMessage.startsWith("Update")
                            ? "Update metadata could not be read."
                            : "Invalid release integrity metadata.");
        }
    }

    private static Selected selectStable(Object parsed) {
        Map<String, Object> release = asMap(parsed);
        if (release == null) {
            throw new IllegalArgumentException("Stable updates only accept the latest release.");
        }
        if (flag(release, "draft") || flag(release, "prerelease")) {
            throw new IllegalArgumentException("No stable release is available.");
        }
        return fromRelease(release, false);
    }

    private static Selected selectDevelopment(Object parsed) {
        List<Object> list = asList(parsed);
        if (list == null) {
            throw new IllegalArgumentException("Development updates only accept the public release list.");
        }
        // GitHub's release list is not guaranteed to be ordered by version.
        // In the actual v0.16.12 feed dev.9 appears before dev.10. Compare
        // numeric version components rather than treating the first item (or
        // lexicographic "dev.9" > "dev.10") as the newest version.
        int limit = Math.min(list.size(), 20);
        Selected newest = null;
        for (int index = 0; index < limit; index++) {
            Map<String, Object> release = asMap(list.get(index));
            if (release == null) continue;
            try {
                if (flag(release, "draft") || !flag(release, "prerelease")) continue;
                String tag = text(release, "tag_name");
                if (!devTag(tag)) continue;
                compareDevTags(tag, tag); // Reject unparseable numeric components even if first.
                if (newest == null || compareDevTags(tag, newest.tag) > 0) {
                    // Validate exact trusted URLs, size and both required assets
                    // BEFORE this candidate can replace the previous valid one.
                    newest = fromRelease(release, true);
                }
            } catch (IllegalArgumentException ignored) {
                // Skip a malformed or unsafe higher tag and keep a valid older release.
            }
        }
        if (newest != null) return newest;
        throw new IllegalArgumentException(
                "No development release is published. Install the signed Actions artifact instead.");
    }

    /** Numeric major.minor.patch-dev.build ordering; large/unparseable tags are rejected. */
    static int compareDevTags(String left, String right) {
        if (!devTag(left) || !devTag(right)) {
            throw new IllegalArgumentException("Invalid development release tag.");
        }
        String[] a = left.substring(1).replace("-dev.", ".").split("\\.");
        String[] b = right.substring(1).replace("-dev.", ".").split("\\.");
        if (a.length != 4 || b.length != 4) {
            throw new IllegalArgumentException("Invalid development release tag.");
        }
        try {
            for (int i = 0; i < 4; i++) {
                int order = Long.compare(Long.parseLong(a[i]), Long.parseLong(b[i]));
                if (order != 0) return order;
            }
            return 0;
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Invalid development release tag.");
        }
    }

    private static Selected fromRelease(Map<String, Object> release, boolean development) {
        String tag = text(release, "tag_name");
        if (development) {
            if (!devTag(tag)) throw new IllegalArgumentException("Unexpected release tag.");
        } else if (!ReleaseAssets.stableTag(tag)) {
            throw new IllegalArgumentException("Unexpected release tag.");
        }
        List<Object> assets = asList(release.get("assets"));
        if (assets == null) throw new IllegalArgumentException("Release needs app-release.apk and update.json.");
        String apk = null;
        String metadata = null;
        long size = -1;
        for (Object asset : assets) {
            Map<String, Object> item = asMap(asset);
            if (item == null) continue;
            String filename = text(item, "name");
            if ("app-release.apk".equals(filename)) {
                apk = text(item, "browser_download_url");
                size = whole(item.get("size"));
            } else if ("update.json".equals(filename)) {
                metadata = text(item, "browser_download_url");
            }
        }
        if (apk == null || apk.isEmpty() || metadata == null || metadata.isEmpty()) {
            throw new IllegalArgumentException("Release needs app-release.apk and update.json.");
        }
        ReleaseAssets.validateAssetUrl(apk, tag, "app-release.apk");
        ReleaseAssets.validateAssetUrl(metadata, tag, "update.json");
        if (size < 4096 || size > 150L * 1024L * 1024L) {
            throw new IllegalArgumentException("Invalid APK file size in release.");
        }
        return new Selected(tag, text(release, "body"), apk, metadata, size);
    }

    private static boolean flag(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        throw new IllegalArgumentException("Update metadata could not be read.");
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : "";
    }

    private static long whole(Object value) {
        return value instanceof Long ? (Long) value : -1L;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    /** Strict JSON for the two release documents. Rejects trailing junk. */
    private static final class Json {
        static final class Syntax extends RuntimeException { }

        private static final Object NOT_WHOLE = new Object();
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object read(String body) {
            Json parser = new Json(body);
            Object value = parser.value(0);
            parser.skip();
            if (parser.i != body.length()) throw new Syntax();
            return value;
        }

        private Object value(int depth) {
            if (depth > 12) throw new Syntax();
            skip();
            if (i >= s.length()) throw new Syntax();
            char c = s.charAt(i);
            if (c == '{') return object(depth);
            if (c == '[') return array(depth);
            if (c == '"') return string();
            if (c == 't') return literal("true", Boolean.TRUE);
            if (c == 'f') return literal("false", Boolean.FALSE);
            if (c == 'n') return literal("null", null);
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw new Syntax();
        }

        private Map<String, Object> object(int depth) {
            i++;
            Map<String, Object> map = new HashMap<>();
            skip();
            if (take('}')) return map;
            while (true) {
                skip();
                if (i >= s.length() || s.charAt(i) != '"') throw new Syntax();
                String key = string();
                skip();
                if (!take(':')) throw new Syntax();
                map.put(key, value(depth + 1));
                skip();
                if (take('}')) return map;
                if (!take(',')) throw new Syntax();
            }
        }

        private List<Object> array(int depth) {
            i++;
            List<Object> list = new ArrayList<>();
            skip();
            if (take(']')) return list;
            while (true) {
                list.add(value(depth + 1));
                skip();
                if (take(']')) return list;
                if (!take(',')) throw new Syntax();
            }
        }

        private String string() {
            i++;
            StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return out.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw new Syntax();
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"':
                        case '\\':
                        case '/':
                            out.append(esc);
                            break;
                        case 'b': out.append('\b'); break;
                        case 'f': out.append('\f'); break;
                        case 'n': out.append('\n'); break;
                        case 'r': out.append('\r'); break;
                        case 't': out.append('\t'); break;
                        case 'u':
                            int hex = 0;
                            for (int k = 0; k < 4; k++) {
                                if (i >= s.length()) throw new Syntax();
                                char h = s.charAt(i++);
                                hex <<= 4;
                                if (h >= '0' && h <= '9') hex += h - '0';
                                else if (h >= 'a' && h <= 'f') hex += h - 'a' + 10;
                                else if (h >= 'A' && h <= 'F') hex += h - 'A' + 10;
                                else throw new Syntax();
                            }
                            out.append((char) hex);
                            break;
                        default:
                            throw new Syntax();
                    }
                    continue;
                }
                if (c < 0x20) throw new Syntax();
                out.append(c);
            }
            throw new Syntax();
        }

        private Object number() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            if (i >= s.length() || s.charAt(i) < '0' || s.charAt(i) > '9') throw new Syntax();
            if (s.charAt(i) == '0') {
                i++;
            } else {
                while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
            }
            boolean whole = true;
            if (i < s.length() && s.charAt(i) == '.') {
                whole = false;
                i++;
                if (i >= s.length() || s.charAt(i) < '0' || s.charAt(i) > '9') throw new Syntax();
                while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                whole = false;
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                if (i >= s.length() || s.charAt(i) < '0' || s.charAt(i) > '9') throw new Syntax();
                while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
            }
            if (!whole) return NOT_WHOLE;
            try {
                return Long.parseLong(s.substring(start, i));
            } catch (NumberFormatException error) {
                return NOT_WHOLE;
            }
        }

        private Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) throw new Syntax();
            i += word.length();
            return value;
        }

        private void skip() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return;
                i++;
            }
        }

        private boolean take(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }
    }
}
