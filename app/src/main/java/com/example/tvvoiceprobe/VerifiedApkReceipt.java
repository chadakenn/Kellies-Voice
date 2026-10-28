package com.example.tvvoiceprobe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;

/**
 * Private-cache proof of what was checked at download time.
 * This is not a replacement for Android's APK signing verification.
 * A deferred install must rehash the cached APK and re-read its manifest.
 */
final class VerifiedApkReceipt {
    private static final long MAX_APK_BYTES = 150L * 1024L * 1024L;
    final int code;
    final String name;
    final String sha256;
    final long size;
    final String channel;

    VerifiedApkReceipt(int code, String name, String sha256, long size, String channel) {
        if (code <= 0 || code > 1000000 || name == null || name.isEmpty()
                || sha256 == null || !sha256.matches("[a-f0-9]{64}")
                || size < 4096 || size > MAX_APK_BYTES
                || !(UpdateChannel.STABLE.equals(channel)
                    || UpdateChannel.DEVELOPMENT.equals(channel))) {
            throw new IllegalStateException("Cached update verification is missing or invalid.");
        }
        this.code = code;
        this.name = name;
        this.sha256 = sha256;
        this.size = size;
        this.channel = channel;
    }

    static VerifiedApkReceipt read(File file) throws Exception {
        if (!file.isFile() || file.length() > 4096) {
            throw new IllegalStateException("Downloaded update needs to be checked again.");
        }
        Properties values = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            values.load(in);
        }
        if (!"1".equals(values.getProperty("format"))) {
            throw new IllegalStateException("Downloaded update needs to be checked again.");
        }
        try {
            return new VerifiedApkReceipt(
                    Integer.parseInt(values.getProperty("code", "")),
                    values.getProperty("name"),
                    values.getProperty("sha256"),
                    Long.parseLong(values.getProperty("size", "")),
                    values.getProperty("channel"));
        } catch (NumberFormatException error) {
            throw new IllegalStateException("Downloaded update needs to be checked again.");
        }
    }

    /** Write the receipt only after full download and APK/signature validation. */
    void save(File destination) throws Exception {
        File pending = new File(destination.getParentFile(), "verified.partial");
        Properties values = new Properties();
        values.setProperty("format", "1");
        values.setProperty("code", Integer.toString(code));
        values.setProperty("name", name);
        values.setProperty("sha256", sha256);
        values.setProperty("size", Long.toString(size));
        values.setProperty("channel", channel);
        try {
            try (FileOutputStream out = new FileOutputStream(pending)) {
                values.store(out, "Kellies Voice verified APK receipt");
                out.getFD().sync();
            }
            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException("Cannot replace cached update verification.");
            }
            if (!pending.renameTo(destination)) {
                throw new IllegalStateException("Cannot save cached update verification.");
            }
        } finally {
            if (pending.exists()) pending.delete();
        }
    }

    void verifyFile(File apk, String currentChannel) throws Exception {
        if (!channel.equals(currentChannel) || !apk.isFile() || apk.length() != size) {
            throw new IllegalStateException("Downloaded update is stale. Check and download again.");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(apk)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", b & 255));
        }
        if (!sha256.equals(hex.toString())) {
            throw new IllegalStateException("Cached APK failed SHA-256 verification.");
        }
    }
}
