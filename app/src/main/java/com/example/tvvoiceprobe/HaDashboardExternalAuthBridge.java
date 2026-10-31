package com.example.tvvoiceprobe;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.util.Collections;
import java.util.function.BooleanSupplier;

/** The only external-auth bridge in the screensaver.
 *
 * V2 is required: V1 addJavascriptInterface exposes a credential bridge to
 * cross-origin iframes and is intentionally NOT provided as a fallback.
 * Only the paired HA HTTPS origin, top-level frame and two fixed callbacks are
 * accepted. Never expose the Keystore refresh token to WebView or logcat.
 */
final class HaDashboardExternalAuthBridge {
    private final WebView web;
    private final String haOrigin;
    private final TvRenewableLoginVault vault;
    private final BooleanSupplier screenActive;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean closed;

    static boolean supported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
    }

    HaDashboardExternalAuthBridge(WebView web, String page,
            String haOrigin, TvRenewableLoginVault vault, BooleanSupplier screenActive) {
        if (!supported() || !HaDashboardAuthPolicy.sameOrigin(page, haOrigin))
            throw new IllegalArgumentException("Secure HA dashboard bridge unavailable");
        this.web = web;
        this.haOrigin = haOrigin;
        this.vault = vault;
        this.screenActive = screenActive;
        // AndroidX injects V2 at document start, ONLY into this exact origin.
        // It does not create an unsafe wildcard or cross-origin V1 bridge.
        WebViewCompat.addWebMessageListener(web, "externalAppV2",
                Collections.singleton(HaDashboardAuthPolicy.origin(haOrigin)),
                (view, message, sourceOrigin, isMainFrame, replyProxy) ->
                        onMessage(view, message, sourceOrigin, isMainFrame));
    }

    private boolean allowed(WebView view, String sourceOrigin, boolean isMainFrame) {
        return !closed && screenActive.getAsBoolean() && view == web
                && isMainFrame
                && HaDashboardAuthPolicy.sameOrigin(sourceOrigin, haOrigin)
                && HaDashboardAuthPolicy.sameOrigin(view.getUrl(), haOrigin);
    }

    private void onMessage(WebView source, WebMessageCompat incoming,
            Uri sourceOrigin, boolean isMainFrame) {
        if (sourceOrigin == null || !allowed(source, sourceOrigin.toString(), isMainFrame))
            return;
        try {
            // Ignore untrusted payloads before parsing; frontend only needs
            // a tiny type/callback message, never a large JSON document.
            String raw = incoming.getData();
            if (raw == null || raw.length() > 2048) return;
            JSONObject request = new JSONObject(raw);
            JSONObject payload = request.optJSONObject("payload");
            if (payload == null) return;
            String type = request.optString("type", "");
            String callback = payload.optString("callback", "");
            // Callback is never copied into JavaScript. Only fixed functions.
            if (!HaDashboardAuthPolicy.callbackAllowed(type, callback)) return;
            if ("getExternalAuth".equals(type)) {
                new Thread(() -> {
                    CredentialStore.Connection conn = null;
                    int expiresIn = 0;
                    try {
                        conn = vault.connectionOnWorker();
                        if (conn != null) expiresIn = vault.accessRemainingSeconds();
                    } catch (Exception ignored) {
                        // Fail closed. No secret, URL or exception in diagnostics.
                    }
                    final CredentialStore.Connection ready = conn;
                    final int ttl = expiresIn;
                    main.post(() -> {
                        if (!allowed(web, haOrigin, true)) return;
                        if (ready == null
                                || !HaDashboardAuthPolicy.sameOrigin(ready.url, haOrigin)) {
                            web.evaluateJavascript("window.externalAuthSetToken(false);", null);
                            return;
                        }
                        // JSON-quoted token is a string literal, never executable JS.
                        web.evaluateJavascript("window.externalAuthSetToken(true,{"
                                + "access_token:" + JSONObject.quote(ready.token)
                                + ",expires_in:" + ttl + "});", null);
                    });
                }, "KelliesHaDashboardToken").start();
            } else if ("revokeExternalAuth".equals(type)) {
                new Thread(() -> {
                    boolean revoked = false;
                    try { revoked = vault.revokeOnWorker(); }
                    catch (Exception ignored) {
                        // HA unreachable: report failure, do not claim logout.
                    }
                    final boolean done = revoked;
                    main.post(() -> {
                        if (!allowed(web, haOrigin, true)) return;
                        web.evaluateJavascript("window.externalAuthRevokeToken("
                                + done + ");", null);
                    });
                }, "KelliesHaDashboardRevoke").start();
            }
        } catch (Exception ignored) {
            // Invalid, oversized or unexpected payloads never cause a login.
        }
    }

    void close() {
        closed = true;
        if (supported()) {
            try { WebViewCompat.removeWebMessageListener(web, "externalAppV2"); }
            catch (RuntimeException ignored) { }
        }
    }
}
