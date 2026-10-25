package com.example.tvvoiceprobe;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateChannelTest {
    private static final String STABLE = "{"
            + "\"tag_name\":\"v0.16.9\",\"draft\":false,\"prerelease\":false,\"body\":\"Stable\","
            + "\"assets\":["
            + asset("v0.16.9", "app-release.apk", 5000) + ","
            + asset("v0.16.9", "update.json", 80)
            + "]}";

    private static String asset(String tag, String name, int size) {
        return "{\"name\":\"" + name + "\",\"size\":" + size
                + ",\"browser_download_url\":\"https://github.com/chadakenn/Kellies-Voice/releases/download/"
                + tag + "/" + name + "\"}";
    }

    @Test public void stableIsTheDefaultAndChecksItself() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.normalize(null));
        assertEquals(UpdateChannel.STABLE, UpdateChannel.normalize(""));
        assertEquals(UpdateChannel.STABLE, UpdateChannel.normalize("dev"));
        assertEquals(UpdateChannel.STABLE, UpdateChannel.normalize("Development"));
        assertEquals(UpdateChannel.DEVELOPMENT, UpdateChannel.normalize("development"));
        assertTrue(UpdateChannel.allowsAutomaticCheck("stable"));
        assertTrue(UpdateChannel.allowsAutomaticCheck(null));
        assertFalse(UpdateChannel.allowsAutomaticCheck("development"));
        assertEquals(UpdateChannel.LATEST, UpdateChannel.feedUrl(null));
        assertEquals(UpdateChannel.DEV_FEED, UpdateChannel.feedUrl("development"));
        assertFalse(UpdateChannel.feedUrl("development").contains("actions/artifacts"));
        assertFalse(UpdateChannel.feedUrl("development").contains("token"));
        assertFalse(UpdateChannel.DEV_FEED.contains("releases/latest"));
    }

    @Test public void stableIgnoresPrereleasesAndBadUrls() {
        UpdateChannel.Selected selected = UpdateChannel.select("stable", STABLE);
        assertEquals("v0.16.9", selected.tag);
        assertEquals(5000L, selected.size);
        try {
            UpdateChannel.select("stable", STABLE.replace("\"prerelease\":false", "\"prerelease\":true"));
            throw new AssertionError("prerelease was offered on stable");
        } catch (IllegalArgumentException expected) {
            assertEquals("No stable release is available.", expected.getMessage());
        }
        try {
            UpdateChannel.select("stable", STABLE.replace(
                    "https://github.com/chadakenn/Kellies-Voice/releases/download/v0.16.9/app-release.apk",
                    "https://evil.example/app-release.apk"));
            throw new AssertionError("untrusted asset was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("asset") || expected.getMessage().contains("Release"));
        }
        try {
            UpdateChannel.select("stable", "[" + STABLE + "]");
            throw new AssertionError("a release list was accepted as stable");
        } catch (IllegalArgumentException expected) {
            assertEquals("Stable updates only accept the latest release.", expected.getMessage());
        }
    }

    @Test public void developmentUsesOnlyAPublishedPrerelease() {
        String dev = assetRelease("v0.16.10-dev.1", true);
        String list = "[" + STABLE + "," + dev + "]";
        UpdateChannel.Selected selected = UpdateChannel.select("development", list);
        assertEquals("v0.16.10-dev.1", selected.tag);
        assertTrue(selected.apkUrl.endsWith("/v0.16.10-dev.1/app-release.apk"));
        String draft = assetRelease("v0.16.10-dev.9", true).replace("\"draft\":false", "\"draft\":true");
        UpdateChannel.Selected skipped = UpdateChannel.select("development",
                "[" + draft + "," + dev + "]");
        assertEquals("v0.16.10-dev.1", skipped.tag);
        try {
            UpdateChannel.select("development", "[" + dev.replace(
                    "https://github.com/chadakenn/Kellies-Voice/releases/download/v0.16.10-dev.1/app-release.apk",
                    "https://evil.example/app-release.apk") + "]");
            throw new AssertionError("untrusted development asset was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Actions artifact"));
        }
        try {
            UpdateChannel.select("development", "[" + STABLE + "]");
            throw new AssertionError("stable was offered as development");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Actions artifact"));
        }
        try {
            UpdateChannel.select("development", STABLE);
            throw new AssertionError("latest object was accepted as development");
        } catch (IllegalArgumentException expected) {
            assertEquals("Development updates only accept the public release list.", expected.getMessage());
        }
    }

    @Test public void malformedMetadataAndDowngradesAreRejected() throws Exception {
        JSONObject good = new JSONObject("{\"versionCode\":27,\"versionName\":\"0.16.10-dev.1\","
                + "\"tag\":\"v0.16.10-dev.1\",\"sha256\":\"" + hash() + "\"}");
        UpdateChannel.requireMetadata("development", "v0.16.10-dev.1", good);
        JSONObject broken = new JSONObject(good.toString());
        broken.put("sha256", "zzzz");
        try {
            UpdateChannel.requireMetadata("development", "v0.16.10-dev.1", broken);
            throw new AssertionError("bad hash accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid release integrity metadata.", expected.getMessage());
        }
        JSONObject stableDevName = new JSONObject("{\"versionCode\":30,\"versionName\":\"0.16.11-dev.1\","
                + "\"tag\":\"v0.16.11\",\"sha256\":\"" + hash() + "\"}");
        try {
            UpdateChannel.requireMetadata("stable", "v0.16.11", stableDevName);
            throw new AssertionError("dev name accepted as stable");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid release integrity metadata.", expected.getMessage());
        }
        assertEquals(UpdateChannel.OFFER, UpdateChannel.verdict("development", 28, 27, "0.16.10-dev.1"));
        assertEquals(UpdateChannel.CURRENT, UpdateChannel.verdict("development", 27, 27, "0.16.10-dev.1"));
        assertEquals(UpdateChannel.ROLLBACK_BLOCKED,
                UpdateChannel.verdict("stable", 26, 27, "0.16.10-dev.1"));
        assertEquals(UpdateChannel.OFFER, UpdateChannel.verdict("stable", 28, 27, "0.16.10-dev.1"));
        assertEquals(UpdateChannel.CURRENT, UpdateChannel.verdict("stable", 26, 26, "0.16.9"));
        assertEquals(UpdateChannel.CURRENT, UpdateChannel.verdict("development", 26, 27, "0.16.9"));
        assertFalse(UpdateChannel.devTag("v0.16.10"));
        assertFalse(UpdateChannel.devTag("../v0.16.10-dev.1"));
    }

    private static String assetRelease(String tag, boolean prerelease) {
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":" + prerelease
                + ",\"body\":\"Dev\",\"assets\":["
                + asset(tag, "app-release.apk", 8000) + ","
                + asset(tag, "update.json", 90) + "]}";
    }

    private static String hash() {
        StringBuilder hash = new StringBuilder();
        for (int i = 0; i < 64; i++) hash.append('a');
        return hash.toString();
    }
}
