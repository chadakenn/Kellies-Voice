package com.example.tvvoiceprobe;

/**
 * APK version checks independent of Android's PackageManager, so they can be
 * exercised in host tests. The platform still validates signing permissions
 * and asks the user to approve installing a matching signed APK.
 */
public final class UpdateVersionPolicy {
    private UpdateVersionPolicy() { }

    /**
     * Reject an APK whose manifest disagrees with update.json, or one that is
     * no longer newer than the installed app. Never truncate 64-bit codes.
     */
    public static void requireExpected(long archiveCode, String archiveName,
            long offeredCode, String offeredName, long installedCode) {
        if (archiveCode <= 0 || offeredCode <= 0
                || archiveName == null || offeredName == null
                || archiveName.isEmpty() || !archiveName.equals(offeredName)
                || archiveCode != offeredCode) {
            throw new IllegalStateException("Downloaded APK version does not match the release.");
        }
        if (archiveCode <= installedCode) {
            throw new IllegalStateException("Downloaded APK is not newer than this TV's app.");
        }
    }
}
