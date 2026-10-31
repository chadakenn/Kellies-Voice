package com.example.tvvoiceprobe;

import android.util.Log;
import java.net.URI;

/**
 * Privacy-safe WebView diagnostics for the screensaver dashboard.
 * Never logs tokens, cookies, authorization headers, or full dashboard URLs.
 */
final class ScreensaverWebDiagnostics {
    private static final String TAG = "KellieScreensaverWeb";

    private ScreensaverWebDiagnostics() { }

    /** Scheme + host class only. No path, query, fragment, or credentials. */
    static String safeSummary(String url) {
        if (url == null || url.isEmpty()) return "empty";
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "?" : uri.getScheme().toLowerCase();
            String host = uri.getHost();
            String hostClass = "unknown";
            if (host != null) {
                if (host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || host.contains(":"))
                    hostClass = "ip";
                else
                    hostClass = "hostname";
            }
            int port = uri.getPort();
            return scheme + " " + hostClass + (port > 0 ? ":" + port : "");
        } catch (Exception ignored) {
            return "invalid";
        }
    }

    static void pageStarted(String url) {
        Log.i(TAG, "page started: " + safeSummary(url));
    }

    static void pageFinished(String url) {
        Log.i(TAG, "page finished: " + safeSummary(url));
    }

    static void loadError(String url, int code) {
        // NEVER accept or log WebView's free-form error description. Some
        // providers include credentials, fragments or unescaped content
        // without an HTTP(S) prefix, defeating naive regex redaction.
        Log.w(TAG, "load error code " + code + " for " + safeSummary(url));
    }

    /**
     * Fixed request categories only. Even the flow ID after /auth/login_flow/
     * is untrusted secret-like material and MUST NOT be logged or echoed.
     * Do not return URL, host, path, query, fragment, or free-form error data.
     */
    static String requestKind(String rawUrl) {
        try {
            String path = URI.create(rawUrl).getPath();
            if (path == null) return "other";
            if (path.equals("/auth/login_flow") || path.startsWith("/auth/login_flow/"))
                return "auth-login-flow";
            if (path.equals("/auth/token")) return "auth-token";
            if (path.equals("/auth/authorize")) return "auth-authorize";
            if (path.equals("/auth/providers")) return "auth-providers";
            return "other";
        } catch (Exception ignored) {
            return "other";
        }
    }

    /** Receives only sanitized integer HTTP code and a fixed path category. */
    static void httpError(String url, int status, boolean mainFrame) {
        int safeStatus = status >= 100 && status <= 599 ? status : 0;
        Log.w(TAG, "HTTP error status=" + safeStatus
                + " kind=" + requestKind(url)
                + " mainFrame=" + mainFrame);
    }

    /** A navigation gate decision, without showing redirect targets or query params. */
    static void blockedNavigation(String url) {
        Log.i(TAG, "blocked navigation kind=" + requestKind(url)
                + " destination=" + safeSummary(url));
    }

    static void authNote(String note) {
        Log.i(TAG, note);
    }
}
