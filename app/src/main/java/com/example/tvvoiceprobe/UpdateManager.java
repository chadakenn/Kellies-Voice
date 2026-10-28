package com.example.tvvoiceprobe;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-approved, non-root in-app GitHub Releases updater.
 * Never downloads an APK from an arbitrary URL or bypasses Android's installer.
 * Validates the release checksum and package identity, then lets Android
 * enforce the same package signature at install time.
 */
public final class UpdateManager {
    private static final long MAX_APK_BYTES = 150L * 1024L * 1024L;
    private final Activity activity;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private volatile Release available;
    private volatile boolean busy;
    private volatile boolean closed;
    /** Bumped when Stable/Development changes so an older check cannot publish. */
    private volatile int offerEpoch;

    public interface Listener {
        void onUpToDate(String message);
        void onAvailable(String version, String notes);
        void onProgress(String message);
        void onDownloaded();
        void onError(String message);
    }

    private static final class Release {
        final int code;
        final String name;
        final String notes;
        final String sha256;
        final String apkUrl;
        final long size;
        final String channel;
        Release(int code, String name, String notes, String sha256, String apkUrl, long size,
                String channel) {
            this.code = code;
            this.name = name;
            this.notes = notes;
            this.sha256 = sha256;
            this.apkUrl = apkUrl;
            this.size = size;
            this.channel = channel;
        }
    }

    public UpdateManager(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public boolean isBusy() { return busy; }

    /** Stop queued update work and prevent stale Activity callbacks. */
    public void close() {
        closed = true;
        offerEpoch++;
        available = null;
        ui.removeCallbacksAndMessages(null);
        background.shutdownNow();
    }

    public void check() {
        if (closed || busy) return;
        busy = true;
        final int epoch = offerEpoch;
        background.execute(() -> {
            String channel = UpdateChannel.STABLE;
            try {
                channel = UpdateChannel.normalize(new OverlayPreferences(activity).updateChannel());
                UpdateChannel.Selected selected = UpdateChannel.select(
                        channel, readText(UpdateChannel.feedUrl(channel), 750000));
                UpdateChannel.Meta meta = UpdateChannel.requireMetadata(
                        channel, selected.tag, readText(selected.metadataUrl, 16384));
                int code = meta.versionCode;
                String name = meta.versionName;
                String hash = meta.sha256;
                String decision = UpdateChannel.verdict(
                        channel, code, installedVersionCode(), activityVersion());
                if (epoch != offerEpoch) return;
                if (!UpdateChannel.OFFER.equals(decision)) {
                    String message = UpdateChannel.ROLLBACK_BLOCKED.equals(decision)
                            ? "Android cannot replace this development build with the current stable release. "
                            + "The next stable version number has to be higher. This TV stays on the installed build."
                            : "You're up to date (v" + activityVersion() + ").";
                    post(() -> {
                        if (epoch != offerEpoch) return;
                        available = null;
                        listener.onUpToDate(message);
                    });
                    return;
                }
                Release offer = new Release(code, name, selected.notes, hash, selected.apkUrl,
                        selected.size, channel);
                post(() -> {
                    if (epoch != offerEpoch) return;
                    String now = UpdateChannel.normalize(
                            new OverlayPreferences(activity).updateChannel());
                    if (!now.equals(offer.channel)) return;
                    available = offer;
                    listener.onAvailable(offer.name, offer.notes == null ? "" : offer.notes);
                });
            } catch (Exception error) {
                if (epoch != offerEpoch) return;
                String message = error.getMessage();
                if (message != null && message.contains("404")) {
                    message = UpdateChannel.DEVELOPMENT.equals(channel)
                            ? "No development release is published. Install the signed Actions artifact instead."
                            : "No public GitHub Release has been published yet.";
                } else if (message == null || message.toLowerCase(Locale.US).contains("http")) {
                    message = "Update check failed. Try again later.";
                }
                final String finalMessage = message;
                post(() -> {
                    if (epoch != offerEpoch) return;
                    listener.onError(finalMessage);
                });
            } finally {
                busy = false;
            }
        });
    }

    /** Drop a downloaded offer when the owner changes Stable or Development. */
    public void clearOffer() {
        offerEpoch++;
        available = null;
        File apk = downloadedApk();
        if (apk.exists()) apk.delete();
        File partial = new File(updateFolder(), "update.partial");
        if (partial.exists()) partial.delete();
    }

    public void download() {
        final Release release = available;
        final int epoch = offerEpoch;
        String channel = UpdateChannel.normalize(new OverlayPreferences(activity).updateChannel());
        if (closed) return;
        if (release == null || busy || !channel.equals(release.channel)) {
            post(() -> listener.onError("Check for updates first."));
            return;
        }
        busy = true;
        background.execute(() -> {
            File temp = new File(updateFolder(), "update.partial");
            File apk = downloadedApk();
            try {
                if (epoch != offerEpoch) {
                    throw new IllegalStateException("Check for updates first.");
                }
                if (!updateFolder().exists() && !updateFolder().mkdirs()) {
                    throw new IllegalStateException("Cannot create APK cache directory.");
                }
                if (temp.exists()) temp.delete();
                if (apk.exists()) apk.delete();
                if (updateFolder().getUsableSpace() < release.size * 2) {
                    throw new IllegalStateException("Not enough free storage on Google TV.");
                }
                HttpURLConnection conn = openHttps(release.apkUrl);
                if (conn.getResponseCode() != 200) {
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    throw new IllegalStateException("APK download failed: HTTP " + code);
                }
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long count = 0;
                int reported = -1;
                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[65536];
                    int amount;
                    while ((amount = input.read(buffer)) != -1) {
                        count += amount;
                        if (count > MAX_APK_BYTES || count > release.size) {
                            throw new IllegalStateException("APK exceeded expected release size.");
                        }
                        sha.update(buffer, 0, amount);
                        output.write(buffer, 0, amount);
                        int percent = (int) (100L * count / release.size);
                        if (percent >= reported + 10 || percent == 100) {
                            reported = percent;
                            int displayPercent = percent;
                            post(() -> listener.onProgress("Downloading: " + displayPercent + "%"));
                        }
                    }
                    output.getFD().sync();
                } finally {
                    conn.disconnect();
                }
                if (count != release.size) {
                    throw new IllegalStateException("Downloaded APK size did not match release.");
                }
                StringBuilder digest = new StringBuilder();
                for (byte value : sha.digest()) {
                    digest.append(String.format(Locale.US, "%02x", value & 255));
                }
                if (!release.sha256.equals(digest.toString())) {
                    throw new IllegalStateException("Downloaded APK failed SHA-256 verification.");
                }
                verifyInstalledIdentity(temp, release);
                if (epoch != offerEpoch || !release.channel.equals(UpdateChannel.normalize(
                        new OverlayPreferences(activity).updateChannel()))) {
                    temp.delete();
                    if (apk.exists()) apk.delete();
                    throw new IllegalStateException("Check for updates first.");
                }
                if (!temp.renameTo(apk)) {
                    throw new IllegalStateException("Could not save the verified APK.");
                }
                post(() -> {
                    if (epoch != offerEpoch) {
                        if (apk.exists()) apk.delete();
                        return;
                    }
                    listener.onDownloaded();
                });
            } catch (Exception error) {
                temp.delete();
                if (epoch != offerEpoch) {
                    if (apk.exists()) apk.delete();
                    return;
                }
                String message = error.getMessage() == null ? "Download failed" : error.getMessage();
                if (message.toLowerCase(Locale.US).contains("http") && !message.contains("HTTP ")) {
                    message = "Download failed. Try again later.";
                }
                final String finalMessage = message;
                post(() -> listener.onError(finalMessage));
            } finally {
                busy = false;
            }
        });
    }

    public boolean downloadedExists() {
        File apk = downloadedApk();
        return apk.isFile() && apk.length() > 4096L;
    }

    public void install() {
        if (closed) return;
        if (!downloadedExists()) {
            listener.onError("No downloaded APK found. Check and download again.");
            return;
        }
        try {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                listener.onError("Allow Kellie's Voice to install unknown apps in Android settings, " +
                        "then return and choose Install downloaded update again.");
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", downloadedApk());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (Exception error) {
            listener.onError("Can't open Android installer. Check unknown-app permission.");
        }
    }

    private void verifyInstalledIdentity(File apk, Release expected) throws Exception {
        PackageManager packages = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo incoming = packages.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (incoming == null) {
            throw new IllegalStateException("Downloaded file is not a valid APK.");
        }
        if (!activity.getPackageName().equals(incoming.packageName)) {
            throw new IllegalStateException("Update APK is a different app and was rejected.");
        }
        PackageInfo installed = packages.getPackageInfo(activity.getPackageName(), flags);
        // The checksum is necessary but not sufficient. Check the actual
        // APK manifest against the advertised authenticated release metadata.
        UpdateVersionPolicy.requireExpected(versionCode(incoming), incoming.versionName,
                expected.code, expected.name, versionCode(installed));
        if (!sameSigners(signaturesOf(installed), signaturesOf(incoming))) {
            throw new IllegalStateException("Update APK is not signed with the installed release key.");
        }
    }

    private static Signature[] signaturesOf(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            Signature[] current = info.signingInfo.getApkContentsSigners();
            if (current != null && current.length > 0) return current;
        }
        return info.signatures == null ? new Signature[0] : info.signatures;
    }

    private static boolean sameSigners(Signature[] installed, Signature[] incoming) {
        if (installed.length == 0 || installed.length != incoming.length) return false;
        HashSet<String> expected = new HashSet<>();
        for (Signature signature : installed) expected.add(signature.toCharsString());
        for (Signature signature : incoming) {
            if (!expected.remove(signature.toCharsString())) return false;
        }
        return expected.isEmpty();
    }

    private long installedVersionCode() throws Exception {
        PackageInfo info = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0);
        return versionCode(info);
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private String activityVersion() {
        try {
            return activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "installed";
        }
    }

    private File updateFolder() { return new File(activity.getCacheDir(), "updates"); }
    private File downloadedApk() { return new File(updateFolder(), "app-release.apk"); }
    private void post(Runnable runnable) {
        if (!closed) ui.post(() -> {
            if (!closed) runnable.run();
        });
    }

    private String readText(String url, int maxBytes) throws Exception {
        HttpURLConnection conn = openHttps(url);
        try {
            if (conn.getResponseCode() != 200) {
                throw new IllegalStateException("Update server returned HTTP " + conn.getResponseCode());
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = conn.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = input.read(buf)) != -1) {
                    if (output.size() + n > maxBytes) {
                        throw new IllegalStateException("Update metadata too large.");
                    }
                    output.write(buf, 0, n);
                }
            }
            return output.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection openHttps(String raw) throws Exception {
        URL next = new URL(raw);
        for (int i = 0; i < 6; i++) {
            if (!"https".equalsIgnoreCase(next.getProtocol())) {
                throw new SecurityException("Refusing insecure update URL.");
            }
            String host = next.getHost().toLowerCase(Locale.US);
            if (!(host.equals("api.github.com") || host.equals("github.com")
                    || host.equals("objects.githubusercontent.com")
                    || host.equals("release-assets.githubusercontent.com")
                    || host.equals("github-releases.githubusercontent.com"))) {
                throw new SecurityException("Untrusted APK download host.");
            }
            HttpURLConnection conn = (HttpURLConnection) next.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Kellies-Voice-Updater");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) throw new IllegalStateException("Update redirect missing.");
                next = new URL(next, location);
                continue;
            }
            return conn;
        }
        throw new IllegalStateException("Too many update download redirects.");
    }
}
