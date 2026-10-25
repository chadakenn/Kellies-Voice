package com.example.tvvoiceprobe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Chooses a public GitHub release for the TV updater.
 * Stable is the default. Development is opt-in and only accepts a published
 * prerelease. No token, Actions artifact, or private URL is ever selected.
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

    public static Selected select(String channel, String payload) {
        if (payload == null) throw new IllegalArgumentException("Update metadata was empty.");
        String body = payload.trim();
        if (body.isEmpty() || body.length() > 750000) {
            throw new IllegalArgumentException("Update metadata was empty.");
        }
        try {
            if (DEVELOPMENT.equals(normalize(channel))) return selectDevelopment(body);
            return selectStable(body);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Update metadata could not be read.");
        }
    }

    public static void requireMetadata(String channel, String tag, JSONObject info) {
        if (info == null || tag == null) {
            throw new IllegalArgumentException("Invalid release integrity metadata.");
        }
        String metaTag = info.optString("tag", "");
        String name = info.optString("versionName", "");
        String hash = info.optString("sha256", "").toLowerCase(java.util.Locale.US);
        int code = info.optInt("versionCode", -1);
        if (!tag.equals(metaTag) || !hash.matches("[a-f0-9]{64}") || code <= 0 || code > 1_000_000) {
            throw new IllegalArgumentException("Invalid release integrity metadata.");
        }
        if (DEVELOPMENT.equals(normalize(channel))) {
            if (!devTag(tag) || !tag.equals("v" + name)) {
                throw new IllegalArgumentException("Invalid release integrity metadata.");
            }
            return;
        }
        if (!ReleaseAssets.stableTag(tag) || name.isEmpty() || name.contains("-dev")) {
            throw new IllegalArgumentException("Invalid release integrity metadata.");
        }
    }

    /**
     * @return {@link #OFFER} only when the published code is newer than the
     *         installed app. Stable cannot replace a newer development build.
     */
    public static String verdict(String channel, int offeredCode, int installedCode, String installedName) {
        if (offeredCode > installedCode) return OFFER;
        boolean installedDev = installedName != null && installedName.contains("-dev");
        if (STABLE.equals(normalize(channel)) && installedDev) return ROLLBACK_BLOCKED;
        return CURRENT;
    }

    private static Selected selectStable(String body) throws JSONException {
        if (!body.startsWith("{")) {
            throw new IllegalArgumentException("Stable updates only accept the latest release.");
        }
        JSONObject release = new JSONObject(body);
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            throw new IllegalArgumentException("No stable release is available.");
        }
        return fromRelease(release, false);
    }

    private static Selected selectDevelopment(String body) throws JSONException {
        if (!body.startsWith("[")) {
            throw new IllegalArgumentException("Development updates only accept the public release list.");
        }
        JSONArray list = new JSONArray(body);
        int limit = Math.min(list.length(), 20);
        for (int index = 0; index < limit; index++) {
            JSONObject release = list.optJSONObject(index);
            if (release == null || release.optBoolean("draft") || !release.optBoolean("prerelease")) {
                continue;
            }
            if (!devTag(release.optString("tag_name", ""))) continue;
            try {
                return fromRelease(release, true);
            } catch (IllegalArgumentException ignored) {
                // A broken prerelease does not hide a later valid one.
            }
        }
        throw new IllegalArgumentException(
                "No development release is published. Install the signed Actions artifact instead.");
    }

    private static Selected fromRelease(JSONObject release, boolean development) throws JSONException {
        String tag = release.optString("tag_name", "");
        if (development) {
            if (!devTag(tag)) throw new IllegalArgumentException("Unexpected release tag.");
        } else if (!ReleaseAssets.stableTag(tag)) {
            throw new IllegalArgumentException("Unexpected release tag.");
        }
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) throw new IllegalArgumentException("Release needs app-release.apk and update.json.");
        String apk = null;
        String metadata = null;
        long size = -1;
        for (int index = 0; index < assets.length(); index++) {
            JSONObject item = assets.optJSONObject(index);
            if (item == null) continue;
            String filename = item.optString("name");
            if ("app-release.apk".equals(filename)) {
                apk = item.optString("browser_download_url");
                size = item.optLong("size", -1);
            } else if ("update.json".equals(filename)) {
                metadata = item.optString("browser_download_url");
            }
        }
        if (apk == null || metadata == null) {
            throw new IllegalArgumentException("Release needs app-release.apk and update.json.");
        }
        ReleaseAssets.validateAssetUrl(apk, tag, "app-release.apk");
        ReleaseAssets.validateAssetUrl(metadata, tag, "update.json");
        if (size < 4096 || size > 150L * 1024L * 1024L) {
            throw new IllegalArgumentException("Invalid APK file size in release.");
        }
        return new Selected(tag, release.optString("body", ""), apk, metadata, size);
    }
}
