package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class UpdateVersionPolicyTest {
    @Test public void acceptsOnlyExactReleaseVersionAndNewerArchive() {
        UpdateVersionPolicy.requireExpected(55L, "0.16.15", 55L,
                "0.16.15", 54L);
    }

    @Test public void rejectsMismatchedManifestVersionNameAndCode() {
        reject(55L, "0.16.14", 55L, "0.16.15", 50L);
        reject(56L, "0.16.15", 55L, "0.16.15", 50L);
        reject(54L, "0.16.15", 55L, "0.16.15", 50L);
        reject(55L, null, 55L, "0.16.15", 50L);
        reject(0L, "0.16.15", 55L, "0.16.15", 50L);
    }

    @Test public void rejectsStaleOffersAndLongVersionOverflow() {
        reject(55L, "0.16.15", 55L, "0.16.15", 55L);
        reject(55L, "0.16.15", 55L, "0.16.15", 56L);
        // Android's versionCodeMajor allows versions above signed 32-bit range.
        reject(55L, "0.16.15", 55L, "0.16.15", 4294967348L);
        assertEquals(UpdateChannel.CURRENT, UpdateChannel.verdict(
                UpdateChannel.DEVELOPMENT, 52L, 4294967348L, "0.16.12-dev.11"));
        assertEquals(UpdateChannel.OFFER, UpdateChannel.verdict(
                UpdateChannel.STABLE, 4294967349L, 4294967348L, "0.16.12-dev.11"));
    }

    private static void reject(long archiveCode, String archiveName,
            long offeredCode, String offeredName, long installedCode) {
        try {
            UpdateVersionPolicy.requireExpected(archiveCode, archiveName,
                    offeredCode, offeredName, installedCode);
            fail("Unsafe APK accepted");
        } catch (IllegalStateException expected) {
            // Expected: no unexpected APK may reach the Android installer.
        }
    }
}
