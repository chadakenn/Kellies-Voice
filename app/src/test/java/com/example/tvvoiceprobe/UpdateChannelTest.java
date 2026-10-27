package com.example.tvvoiceprobe;

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
        try {
            UpdateChannel.select("stable", STABLE + " trailing");
            throw new AssertionError("trailing junk was accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("Update metadata could not be read.", expected.getMessage());
        }
        String escaped = STABLE.replace("github.com", "github\\u002ecom");
        assertEquals("v0.16.9", UpdateChannel.select("stable", escaped).tag);
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

    @Test public void developmentChoosesHighestNumericVersionNotFirstRelease() {
        // Real GitHub feed order on October 26: dev.9, dev.8 ... dev.10.
        String list = "[" + STABLE + ","
                + assetRelease("v0.16.12-dev.9", true) + ","
                + assetRelease("v0.16.12-dev.8", true) + ","
                + assetRelease("v0.16.12-dev.10", true) + "]";
        assertEquals("v0.16.12-dev.10",
                UpdateChannel.select(UpdateChannel.DEVELOPMENT, list).tag);
        assertTrue(UpdateChannel.compareDevTags("v0.16.12-dev.10", "v0.16.12-dev.9") > 0);
        assertTrue(UpdateChannel.compareDevTags("v0.17.0-dev.1", "v0.16.12-dev.999") > 0);
        assertTrue(UpdateChannel.compareDevTags("v1.0.0-dev.1", "v0.99.99-dev.99") > 0);
        assertEquals(0, UpdateChannel.compareDevTags("v0.16.12-dev.10", "v0.16.12-dev.10"));
    }

    @Test public void unsafeOrInvalidHigherPrereleaseCannotDisplaceSafeRelease() {
        String good = assetRelease("v0.16.12-dev.9", true);
        String missingMetadata = assetRelease("v0.16.12-dev.11", true)
                .replace("update.json", "missing.json");
        String unsafe = assetRelease("v0.16.12-dev.12", true)
                .replace("https://github.com/chadakenn/Kellies-Voice/releases/download/v0.16.12-dev.12/app-release.apk",
                        "https://evil.example/untrusted.apk");
        String draft = assetRelease("v0.16.12-dev.13", true)
                .replace("\\"draft\\":false", "\\"draft\\":true");
        String tooLarge = assetRelease("v99999999999999999999999.0.0-dev.9", true);
        String stable = assetRelease("v0.17.0", false);
        String list = "[" + missingMetadata + "," + unsafe + "," + draft
                + "," + stable + "," + tooLarge + "," + good + "]";
        assertEquals("v0.16.12-dev.9",
                UpdateChannel.select(UpdateChannel.DEVELOPMENT, list).tag);
        assertEquals("v0.16.12-dev.9",
                UpdateChannel.select(UpdateChannel.DEVELOPMENT, "[" + good + "," + unsafe + "]").tag);
        assertEquals("v0.16.12-dev.9",
                UpdateChannel.select(UpdateChannel.DEVELOPMENT, "[" + tooLarge + "," + good + "]").tag);
    }

    @Test public void malformedMetadataAndDowngradesAreRejected() {
        String good = "{\"versionCode\":27,\"versionName\":\"0.16.10-dev.1\","
                + "\"tag\":\"v0.16.10-dev.1\",\"sha256\":\"" + hash() + "\"}";
        UpdateChannel.Meta meta = UpdateChannel.requireMetadata("development", "v0.16.10-dev.1", good);
        assertEquals(27, meta.versionCode);
        assertEquals("0.16.10-dev.1", meta.versionName);
        try {
            UpdateChannel.requireMetadata("development", "v0.16.10-dev.1",
                    good.replace(hash(), "zzzz"));
            throw new AssertionError("bad hash accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid release integrity metadata.", expected.getMessage());
        }
        String stableDevName = "{\"versionCode\":30,\"versionName\":\"0.16.11-dev.1\","
                + "\"tag\":\"v0.16.11\",\"sha256\":\"" + hash() + "\"}";
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
