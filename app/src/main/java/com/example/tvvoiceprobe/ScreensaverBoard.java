package com.example.tvvoiceprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.ui.AspectRatioFrameLayout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Frigate page, or a small camera grid, with a Close button.
 * The Home Assistant token is sent only as an image request header, never in a web address.
 */
final class ScreensaverBoard {
    interface Host { void close(); }

    private static final int MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024;
    private static final long REFRESH_MS = 5000L;
    // HA's renewable access tokens expire after 30 minutes. Re-create the
    // screen's camera streams before expiry; no saved manual-token fallback.
    private static final long RENEWABLE_RELOAD_MS = 20 * 60_000L;

    private final Context context;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final List<Call> calls = new ArrayList<>();
    private OkHttpClient http;
    private WebView web;
    private HaDashboardExternalAuthBridge haWebAuth;
    private View dashboardLoading;
    private Button dashboardLoginButton;
    private boolean dashboardRevealed;
    private long dashboardReadySinceMs;
    private long dashboardLoadingStartedMs;
    private ScreensaverLiveGrid liveGrid;
    private ImageView[] gridPhotos;
    private View[] gridCells;
    private View firstGridCell;
    private FrameLayout fullscreenPanel;
    private ImageView fullscreenPhoto;
    private View fullscreenBack;
    private Button closeControl;
    private OnBackInvokedDispatcher overlayBackDispatcher;
    private OnBackInvokedCallback overlayBackCallback;
    private int fullscreenSlot = -1;
    private TextView remotePointer;
    private boolean browsing;
    /** Presentation only: HA UI needs native responsive sizing and non-overlapping TV controls. */
    private boolean haDashboardPage;
    private final ScreensaverRemoteCursor cursor = new ScreensaverRemoteCursor();
    private int generation;
    private boolean stopped;
    private final Runnable onSettings = () -> main.post(() -> {
        if (!stopped) start();
    });

    ScreensaverBoard(Context context, Host host) {
        this.context = context;
        this.host = host;
        root = new FrameLayout(context) {
            // The focused camera tile or footer button receives key events before
            // a root OnKeyListener. Intercept before dispatching to children.
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                // Android WebView consumes TV D-pad keys for HTML focus before
                // a child OnKeyListener can reliably route them. In explicit
                // pointer mode, intercept at the parent BEFORE dispatching to
                // the page, including key-up, so Forgot password and other
                // browser links cannot steal directional navigation.
                if (browsing && web != null && isRemotePointerKey(event.getKeyCode())) {
                    return remoteKey(event.getKeyCode(), event);
                }
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                        onBack();
                    }
                    return true; // Consume DOWN and UP, not just the release.
                }
                return super.dispatchKeyEvent(event);
            }
        };
        root.setBackgroundColor(Color.BLACK);
        root.setFocusable(true);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                // Android 13+ can deliver Back through the window dispatcher,
                // including the focusable TYPE_APPLICATION_OVERLAY service.
                if (Build.VERSION.SDK_INT >= 33 && context instanceof ScreensaverWatchService) {
                    OnBackInvokedDispatcher dispatcher = view.findOnBackInvokedDispatcher();
                    if (dispatcher != null) {
                        overlayBackDispatcher = dispatcher;
                        overlayBackCallback = ScreensaverBoard.this::onBack;
                        dispatcher.registerOnBackInvokedCallback(
                                OnBackInvokedDispatcher.PRIORITY_DEFAULT, overlayBackCallback);
                    }
                }
            }
            @Override public void onViewDetachedFromWindow(View view) {
                if (Build.VERSION.SDK_INT >= 33 && overlayBackDispatcher != null
                        && overlayBackCallback != null) {
                    overlayBackDispatcher.unregisterOnBackInvokedCallback(overlayBackCallback);
                }
                overlayBackCallback = null;
                overlayBackDispatcher = null;
            }
        });
        ScreensaverSignals.listen(onSettings);
    }

    View view() { return root; }

    /** One Back action for legacy remote keys and Android 13+ system Back. */
    private void onBack() {
        if (stopped) return;
        if (handleBack()) return;
        // The HA screensaver is an appliance overlay, not a browser tab:
        // one Back always closes it. Frigate retains its existing history path.
        if (haDashboardPage && web != null) {
            host.close();
            return;
        }
        // Preserve the Frigate webpage's own remote browsing Back behavior.
        if (browsing && web != null) {
            if (web.canGoBack()) {
                web.goBack();
            } else {
                browsing = false;
                if (remotePointer != null) remotePointer.setVisibility(View.GONE);
                root.requestFocus();
            }
            return;
        }
        host.close();
    }

    /** Consumed by the DreamService, overlay and preview before exiting the dashboard. */
    boolean handleBack() {
        if (fullscreenSlot < 0) return false;
        exitFullscreen();
        return true;
    }

    void start() {
        // A camera-list/size preference update must not leak the old refresh
        // loop, video WebView, outstanding image requests or bitmap cache.
        if (generation > 0) {
            stop();
            ScreensaverSignals.listen(onSettings);
        }
        stopped = false;
        generation++;
        OverlayPreferences prefs = new OverlayPreferences(context);
        root.removeAllViews();
        closeControl = null;
        gridPhotos = null;
        gridCells = null;
        firstGridCell = null;
        fullscreenPanel = null;
        fullscreenPhoto = null;
        fullscreenBack = null;
        fullscreenSlot = -1;
        remotePointer = null;
        browsing = false;
        haDashboardPage = false;
        dashboardLoading = null;
        dashboardLoginButton = null;
        dashboardRevealed = false;
        dashboardReadySinceMs = 0L;
        dashboardLoadingStartedMs = 0L;
        if (!prefs.screensaverDashboard()) {
            showMessage("Frigate screensaver is off. Turn it on in Kellie's Voice settings, then press Close.");
            root.addView(closeButton());
            return;
        }
        String page = prefs.screensaverUrl();
        String[] cameras = ScreensaverDashboard.cameraList(prefs.screensaverCameras());
        haDashboardPage = !page.isEmpty()
                && ScreensaverDashboard.isHomeAssistantPage(page, prefs.screensaverHaExternalAuth());
        if (!page.isEmpty()) {
            try {
                if (prefs.screensaverHaExternalAuth()) showAuthenticatedHaPage(page);
                else if (prefs.screensaverLanHttpOnly() && page.startsWith("http://")
                        && !LanHttpDashboardPolicy.isStrictLanHttp(page)) {
                    showMessage("LAN HTTP restriction: enter a private numeric IP address "
                            + "(10.x.x.x, 172.16–31.x.x or 192.168.x.x) or turn OFF "
                            + "this opt-in setting. No credentials were shared.");
                } else showPage(page, null);
            } catch (RuntimeException ignored) {
                showMessage("This TV couldn't open the dashboard page.");
            }
        } else if (cameras.length > 0) showCameras(cameras);
        else showMessage("No Frigate URL or HA cameras are saved on this TV.\n"
                + "If you selected cameras in Home Assistant, turn on Home Assistant layout "
                + "in Kellie's Voice settings, then check Screensaver status & permissions "
                + "and choose Resync HA cameras.");
        // HA browsing starts with its visible remote pointer, and Back exits.
        // Avoid covering the user's dashboard with redundant app controls.
        // Retain both controls for native cameras and ordinary Frigate pages.
        if (web != null && !haDashboardPage) root.addView(browseButton());
        if (!haDashboardPage) {
            closeControl = closeButton();
            root.addView(closeControl);
        }
        if (firstGridCell != null) firstGridCell.post(() -> {
            if (!stopped && fullscreenSlot < 0) firstGridCell.requestFocus();
        });
    }

    void stop() {
        stopped = true;
        dashboardLoading = null;
        dashboardLoginButton = null;
        dashboardRevealed = false;
        dashboardReadySinceMs = 0L;
        dashboardLoadingStartedMs = 0L;
        browsing = false;
        remotePointer = null;
        fullscreenSlot = -1;
        fullscreenPanel = null;
        fullscreenPhoto = null;
        fullscreenBack = null;
        closeControl = null;
        gridPhotos = null;
        gridCells = null;
        firstGridCell = null;
        generation++;
        ScreensaverSignals.unlisten(onSettings);
        main.removeCallbacksAndMessages(null);
        List<Call> pending;
        synchronized (calls) {
            pending = new ArrayList<>(calls);
            calls.clear();
        }
        for (Call call : pending) call.cancel();
        if (liveGrid != null) {
            liveGrid.stop();
            liveGrid = null;
        }
        if (http != null) {
            http.dispatcher().executorService().shutdown();
            http.connectionPool().evictAll();
            http = null;
        }
        if (haWebAuth != null) {
            haWebAuth.close();
            haWebAuth = null;
        }
        if (web != null) {
            web.stopLoading();
            if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
    }

    /** Optional HA dashboard mode. The four-camera grid and ordinary Frigate
     * WebView never receive HA credentials. The v3 pairing and HTTPS origin
     * must be verified before even creating the authenticated WebView.
     */
    private void showAuthenticatedHaPage(String page) {
        final int current = generation;
        TextView notice = showMessage("Connecting securely to Home Assistant dashboard…");
        if (!HaDashboardExternalAuthBridge.supported()) {
            notice.setText("This Android TV WebView cannot safely sign into an HA dashboard. "
                    + "Update Android System WebView or disable HA dashboard sign-in.");
            return;
        }
        TvRenewableLoginVault vault = new TvRenewableLoginVault(context);
        if (!vault.blocksManualFallback() || !vault.hasActiveLogin()) {
            notice.setText("HA dashboard sign-in requires the owner-approved TV login. "
                    + "Connect the TV from Kellie's Voice SETTINGS first.");
            return;
        }
        new Thread(() -> {
            CredentialStore.Connection authorized = null;
            String signedUrl = "";
            try {
                authorized = vault.connectionOnWorker();
                if (authorized != null) signedUrl =
                        HaDashboardAuthPolicy.authenticatedUrl(page, authorized.url);
            } catch (Exception ignored) {
                // No token or sensitive HA address in logs.
            }
            final String readyUrl = signedUrl;
            final CredentialStore.Connection ready = authorized;
            main.post(() -> {
                if (stopped || current != generation) return;
                if (ready == null || readyUrl.isEmpty()) {
                    notice.setText("HA dashboard sign-in unavailable. Use an HTTPS dashboard "
                            + "URL on this TV's paired Home Assistant server, or reconnect in SETTINGS.");
                    return;
                }
                try {
                    // All credentials remain native, with a V2 main-frame-only bridge.
                    showPage(readyUrl, ready.url);
                    root.removeView(notice);
                    // HA automatically starts pointer navigation in showPage().
                    // No Browse button covers the dashboard.
                } catch (RuntimeException ignored) {
                    if (haWebAuth != null) { haWebAuth.close(); haWebAuth = null; }
                    if (web != null) {
                        web.stopLoading();
                        if (web.getParent() instanceof ViewGroup)
                            ((ViewGroup) web.getParent()).removeView(web);
                        web.destroy();
                        web = null;
                    }
                    notice.setText("HA dashboard secure WebView failed. No login was shared.");
                }
            });
        }, "KelliesHaDashboardConnect").start();
    }

    private void showPage(String page, String authenticatedHaOrigin) {
        web = new WebView(context);
        web.setBackgroundColor(Color.BLACK);
        WebSettings settings = web.getSettings();
        OverlayPreferences prefs = new OverlayPreferences(context);
        boolean desktop = ScreensaverDashboard.useFrigateDesktopViewport(
                page, prefs.screensaverDesktopPage(), authenticatedHaOrigin != null);
        if (desktop) {
            // Frigate otherwise uses a phone-width WebView viewport on many Android TVs.
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUserAgentString(ScreensaverDashboard.desktopUserAgent(settings.getUserAgentString()));
        } else if (haDashboardPage) {
            // Home Assistant's frontend sets its own responsive meta viewport.
            // Frigate's forced wide layout/overview scale clips HA login on TV.
            settings.setUseWideViewPort(false);
            settings.setLoadWithOverviewMode(false);
            // Keep the installed Android WebView user agent for HA.
        }
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setDomStorageEnabled(true);
        CookieManager cookies = CookieManager.getInstance();
        if (cookies != null) {
            cookies.setAcceptCookie(true);
            cookies.setAcceptThirdPartyCookies(web, false);
        }
        web.setFocusableInTouchMode(true);
        if (authenticatedHaOrigin != null) {
            final WebView loadedWeb = web;
            final int openedGeneration = generation;
            haWebAuth = new HaDashboardExternalAuthBridge(web, page,
                    authenticatedHaOrigin, new TvRenewableLoginVault(context),
                    () -> !stopped && openedGeneration == generation
                            && web == loadedWeb);
        }
        web.setOnKeyListener((view, key, event) -> browsing && remoteKey(key, event));
        final boolean strictLocalHttp = prefs.screensaverLanHttpOnly()
                && LanHttpDashboardPolicy.isStrictLanHttp(page)
                && authenticatedHaOrigin == null;
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                ScreensaverWebDiagnostics.pageStarted(url);
                // Initial HA redirects, including its login and default dashboard,
                // stay behind the native loading surface, never exposed on the TV.
                if (haDashboardPage && !dashboardRevealed) dashboardReadySinceMs = 0L;
            }
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                if (strictLocalHttp && request != null && request.getUrl() != null) {
                    String next = request.getUrl().toString();
                    // Best-effort block for ordinary HTTP(S) subresources:
                    // remote scripts/images/frames are not part of a local-only
                    // dashboard. WebSocket/service-worker behavior may differ.
                    if ((next.startsWith("http://") || next.startsWith("https://"))
                            && !LanHttpDashboardPolicy.allowedNavigation(page, next)) {
                        return new WebResourceResponse("text/plain", "UTF-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                ScreensaverWebDiagnostics.pageFinished(url);
                if (desktop && !stopped && ScreensaverDashboard.sameSite(page, url)) {
                    view.evaluateJavascript(ScreensaverDashboard.desktopViewportScript(
                            ScreensaverDashboard.viewportWidth(prefs.screensaverFit())), null);
                }
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                    WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                if (request != null && request.getUrl() != null && response != null) {
                    // Status and category only: never log auth codes, raw paths,
                    // flow identifiers, cookies, response bodies or headers.
                    ScreensaverWebDiagnostics.httpError(
                            request.getUrl().toString(),
                            response.getStatusCode(), request.isForMainFrame());
                }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    ScreensaverWebDiagnostics.loadError(
                            request.getUrl() == null ? "" : request.getUrl().toString(),
                            error == null ? -1 : error.getErrorCode());
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return true;
                String next = request.getUrl().toString();
                boolean allowed = strictLocalHttp
                        ? LanHttpDashboardPolicy.allowedNavigation(page, next)
                        : ScreensaverDashboard.sameOriginNavigation(page, next);
                if (!allowed) ScreensaverWebDiagnostics.blockedNavigation(
                        page, next, request.isForMainFrame(),
                        request.isRedirect(), request.hasGesture());
                return !allowed;
            }
        });
        FrameLayout.LayoutParams params = cardParams();
        // Let Frigate use the whole TV; Close floats over the bottom-right corner.
        if (params.width == FrameLayout.LayoutParams.MATCH_PARENT) params.bottomMargin = 0;
        // Keep Close / browse controls ABOVE the asynchronously loaded page.
        root.addView(web, 0, params);
        if (haDashboardPage) {
            // A native, opaque surface masks the whole WebView during HA's
            // login, redirect to / and subsequent navigation to the saved path.
            // Nothing is injected into HA's login flow or a URL.
            FrameLayout cover = new FrameLayout(context);
            cover.setBackgroundColor(Color.BLACK);
            TextView loading = new TextView(context);
            loading.setText("Loading Home Assistant dashboard…\nPress Back to exit");
            loading.setTextColor(Color.WHITE);
            loading.setTextSize(20);
            loading.setGravity(Gravity.CENTER);
            cover.addView(loading, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            // Preserve manual login for TVs without automatic trusted-network
            // or secure HA sign-in; keep it hidden during ordinary redirects.
            Button manualLogin = new Button(context);
            manualLogin.setText("Sign in manually");
            manualLogin.setTextColor(Color.WHITE);
            manualLogin.setBackground(controlBackground(0xFF173747, 0xFF24556A));
            manualLogin.setFocusable(true);
            manualLogin.setVisibility(View.GONE);
            FrameLayout.LayoutParams loginParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            loginParams.bottomMargin = dp(44);
            cover.addView(manualLogin, loginParams);
            manualLogin.setOnClickListener(v -> {
                // Explicit user choice: unmask the same-origin WebView and
                // allow interactive Home Assistant login.
                if (stopped || web == null || dashboardRevealed) return;
                dashboardRevealed = true;
                cover.setVisibility(View.GONE);
                if (!browsing) startBrowsing();
            });
            root.addView(cover, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            dashboardLoading = cover;
            dashboardLoginButton = manualLogin;
            dashboardLoadingStartedMs = SystemClock.uptimeMillis();
        }
        final WebView openedWeb = web;
        final int openedGeneration = generation;
        web.loadUrl(page);
        if (haDashboardPage) {
            main.postDelayed(() -> checkDashboardReady(
                    openedWeb, page, openedGeneration), 400L);
        }
    }

    /** Reveal only after the configured HA path AND frontend have settled.
     * HA uses history.pushState, so onPageFinished alone is insufficient.
     * No browser URL, DOM content, credentials or response text enters logs.
     */
    private void checkDashboardReady(WebView openedWeb, String expectedPage, int openedGeneration) {
        if (stopped || openedGeneration != generation || web != openedWeb
                || !haDashboardPage || dashboardRevealed) return;
        openedWeb.evaluateJavascript(ScreensaverDashboard.dashboardReadyScript(expectedPage),
                result -> {
                    if (stopped || openedGeneration != generation || web != openedWeb
                            || dashboardRevealed) return;
                    long now = SystemClock.uptimeMillis();
                    if ("true".equals(result)) {
                        if (dashboardReadySinceMs == 0L) dashboardReadySinceMs = now;
                        if (now - dashboardReadySinceMs >= 1000L) {
                            dashboardRevealed = true;
                            if (dashboardLoading != null)
                                dashboardLoading.setVisibility(View.GONE);
                            // HA D-pad pointer is shown only over the final page.
                            if (!browsing) startBrowsing();
                            return;
                        }
                    } else {
                        dashboardReadySinceMs = 0L;
                    }
                    if (now - dashboardLoadingStartedMs >= 15_000L
                            && dashboardLoginButton != null
                            && dashboardLoginButton.getVisibility() != View.VISIBLE) {
                        // Restore a path for interactive password/user login
                        // when automatic sign-in is unavailable on this TV.
                        dashboardLoginButton.setVisibility(View.VISIBLE);
                        dashboardLoginButton.requestFocus();
                    }
                    main.postDelayed(() -> checkDashboardReady(
                            openedWeb, expectedPage, openedGeneration), 400L);
                });
    }

    /** Explicit remote browsing. WebView itself may not expose Frigate's tiles to D-pad focus. */
    private Button browseButton() {
        Button browse = new Button(context);
        browse.setText(haDashboardPage ? "Browse dashboard" : "Select camera");
        browse.setTextColor(Color.WHITE);
        browse.setTextSize(16);
        browse.setAllCaps(false);
        browse.setFocusable(true);
        browse.setOnClickListener(v -> startBrowsing());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(haDashboardPage ? 208 : 186), dp(52));
        params.gravity = haDashboardPage
                ? Gravity.TOP | Gravity.END : Gravity.BOTTOM | Gravity.START;
        if (haDashboardPage) params.setMargins(dp(16), dp(16), dp(24), dp(16));
        else params.setMargins(dp(16), dp(16), dp(16), dp(16));
        browse.setLayoutParams(params);
        return browse;
    }

    private void startBrowsing() {
        if (web == null || stopped) return;
        if (!browsing) cursor.reset();
        browsing = true;
        if (remotePointer == null) {
            remotePointer = new TextView(context);
            remotePointer.setText("⊕");
            remotePointer.setTextSize(34);
            remotePointer.setTextColor(0xFFFFCB77);
            remotePointer.setShadowLayer(dp(4), 0, 0, Color.BLACK);
            remotePointer.setGravity(Gravity.CENTER);
            remotePointer.setFocusable(false);
            root.addView(remotePointer, new FrameLayout.LayoutParams(dp(48), dp(48)));
        }
        web.requestFocus();
        web.post(this::positionPointer);
    }

    private static boolean isRemotePointerKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return true;
            default:
                return false;
        }
    }

    private boolean remoteKey(int key, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
                cursor.move(ScreensaverRemoteCursor.UP, event.getRepeatCount()); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cursor.move(ScreensaverRemoteCursor.RIGHT, event.getRepeatCount()); break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                cursor.move(ScreensaverRemoteCursor.DOWN, event.getRepeatCount()); break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                cursor.move(ScreensaverRemoteCursor.LEFT, event.getRepeatCount()); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                tapWeb(); return true;
            case KeyEvent.KEYCODE_BACK:
                if (web != null && web.canGoBack()) {
                    web.goBack();
                } else {
                    browsing = false;
                    if (remotePointer != null) remotePointer.setVisibility(View.GONE);
                    root.requestFocus();
                }
                return true;
            default: return false;
        }
        positionPointer();
        if (haDashboardPage && (key == KeyEvent.KEYCODE_DPAD_UP
                || key == KeyEvent.KEYCODE_DPAD_DOWN)) {
            float wheel = cursor.verticalWheelAtEdge(
                    key == KeyEvent.KEYCODE_DPAD_UP
                            ? ScreensaverRemoteCursor.UP : ScreensaverRemoteCursor.DOWN,
                    event.getRepeatCount());
            if (wheel != 0f) scrollDashboard(wheel);
        }
        return true;
    }

    /**
     * Dispatch a genuine mouse-wheel motion to WebView at the remote cursor.
     * This lets the browser scroll the element under the pointer (including
     * nested HA cards) without JavaScript, DOM access or altering credentials.
     * Only HA dashboard screens use this, never the Frigate camera viewer.
     */
    private void scrollDashboard(float axis) {
        if (!haDashboardPage || !browsing || web == null
                || web.getWidth() <= 0 || web.getHeight() <= 0) return;
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = cursor.pixelX(web.getWidth());
        // At a screen edge, use a nearby position within the actual content.
        coords.y = Math.max(web.getHeight() * 0.12f,
                Math.min(web.getHeight() * 0.88f, cursor.pixelY(web.getHeight())));
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, axis);
        long now = SystemClock.uptimeMillis();
        MotionEvent wheel = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_SCROLL, 1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords},
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        try {
            web.dispatchGenericMotionEvent(wheel);
        } finally {
            wheel.recycle();
        }
    }

    private void positionPointer() {
        if (!browsing || web == null || remotePointer == null) return;
        int centerX = web.getLeft() + cursor.pixelX(web.getWidth());
        int centerY = web.getTop() + cursor.pixelY(web.getHeight());
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) remotePointer.getLayoutParams();
        p.gravity = Gravity.TOP | Gravity.START;
        p.leftMargin = centerX - dp(24);
        p.topMargin = centerY - dp(24);
        remotePointer.setLayoutParams(p);
        remotePointer.setVisibility(View.VISIBLE);
        remotePointer.bringToFront();
    }

    private void tapWeb() {
        if (!browsing || web == null || web.getWidth() == 0 || web.getHeight() == 0) return;
        float x = cursor.pixelX(web.getWidth());
        float y = cursor.pixelY(web.getHeight());
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 75, MotionEvent.ACTION_UP, x, y, 0);
        try {
            web.dispatchTouchEvent(down);
            web.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private void showCameras(String[] cameras) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        // cardParams() already reserves the Close-button strip in Large mode.
        // A second bottom inset here previously squeezed the two camera rows upward.
        grid.setPadding(dp(8), dp(8), dp(8), 0);
        grid.setBackgroundColor(Color.BLACK);
        ImageView[] photos = new ImageView[cameras.length];
        gridPhotos = photos;
        gridCells = new View[cameras.length];
        TextureView[] surfaces = new TextureView[cameras.length];
        TextView[] liveStatuses = new TextView[cameras.length];
        OverlayPreferences prefs = new OverlayPreferences(context);
        String decoder = prefs.cameraMode();
        int liveLimit = ScreensaverLiveMode.streamLimit(
                prefs.screensaverLiveMode(), decoder, cameras.length);
        int rows = (cameras.length + 1) / 2;
        int index = 0;
        for (int row = 0; row < rows; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            for (int column = 0; column < 2 && index < cameras.length; column++) {
                final int slot = index;
                FrameLayout cell = new FrameLayout(context);
                cell.setFocusable(true);
                cell.setClickable(true);
                cell.setOnClickListener(v -> showFullscreen(slot, cameras[slot]));
                GradientDrawable focused = new GradientDrawable();
                focused.setColor(Color.TRANSPARENT);
                focused.setStroke(dp(4), 0xFFFFD166);
                GradientDrawable normal = new GradientDrawable();
                normal.setColor(Color.TRANSPARENT);
                StateListDrawable focusRing = new StateListDrawable();
                focusRing.addState(new int[] { android.R.attr.state_focused }, focused);
                focusRing.addState(new int[] {}, normal);
                cell.setForeground(focusRing);
                gridCells[index] = cell;
                if (firstGridCell == null) firstGridCell = cell;
                ImageView photo = new ImageView(context);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photo.setBackgroundColor(0xFF141414);
                cell.addView(photo, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                if (index < liveLimit) {
                    TextureView surface = new TextureView(context);
                    surface.setOpaque(false);
                    surface.setAlpha(0f);
                    cell.addView(surface, new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
                    surfaces[index] = surface;
                    TextView status = new TextView(context);
                    status.setText("Snapshot · connecting to live");
                    status.setTextColor(Color.WHITE);
                    status.setTextSize(12);
                    status.setPadding(dp(6), dp(4), dp(6), dp(4));
                    status.setBackgroundColor(0x99000000);
                    FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
                    statusParams.gravity = Gravity.TOP | Gravity.END;
                    cell.addView(status, statusParams);
                    liveStatuses[index] = status;
                }
                TextView name = new TextView(context);
                name.setText(label(cameras[index]));
                name.setTextColor(Color.WHITE);
                name.setTextSize(16);
                name.setPadding(dp(8), dp(4), dp(8), dp(4));
                name.setBackgroundColor(0x99000000);
                FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                nameParams.gravity = Gravity.BOTTOM | Gravity.START;
                cell.addView(name, nameParams);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                cellParams.setMargins(dp(6), dp(6), dp(6), dp(6));
                line.addView(cell, cellParams);
                photos[index] = photo;
                index++;
            }
            grid.addView(line, rowParams);
        }
        root.addView(grid, cardParams());
        final int current = generation;
        // v3 owner-approved TV logins live in Android Keystore, not in the
        // legacy manual CredentialStore. Never block the screensaver's main
        // thread with an HTTPS token refresh, and never fall back to a stored
        // manual owner token if the renewable grant is unavailable/revoked.
        TvRenewableLoginVault vault = new TvRenewableLoginVault(context);
        boolean renewable = vault.blocksManualFallback();
        if (!TvOverlayLoginPolicy.mayUseManual(renewable)) {
            TextView signInNotice = showMessage("Connecting securely to Home Assistant cameras…");
            new Thread(() -> {
                CredentialStore.Connection ready = null;
                try {
                    ready = vault.connectionOnWorker();
                } catch (Exception ignored) {
                    // No token, URL, camera address or credential in logs.
                }
                final CredentialStore.Connection authorized = ready;
                main.post(() -> {
                    if (stopped || current != generation) return;
                    if (authorized == null) {
                        signInNotice.setText("TV login unavailable. Open SETTINGS to reconnect to Home Assistant.");
                        return;
                    }
                    root.removeView(signInNotice);
                    beginCameraStreams(authorized, cameras, photos, surfaces, liveStatuses,
                            liveLimit, decoder, current);
                    // Reload only the camera view. Current HA access tokens
                    // must not be reused after they expire during a long dream.
                    main.postDelayed(() -> {
                        if (!stopped && current == generation) start();
                    }, RENEWABLE_RELOAD_MS);
                });
            }, "KelliesScreensaverRenewableLogin").start();
            return;
        }
        // Manual-only installations keep their previous screensaver path.
        CredentialStore.Connection manual = signedIn();
        if (manual == null) {
            showMessage("Sign in to Home Assistant to load these cameras.");
            return;
        }
        beginCameraStreams(manual, cameras, photos, surfaces, liveStatuses,
                liveLimit, decoder, current);
    }

    private void beginCameraStreams(CredentialStore.Connection connection, String[] cameras,
            ImageView[] photos, TextureView[] surfaces, TextView[] liveStatuses,
            int liveLimit, String decoder, int current) {
        if (stopped || current != generation || connection == null) return;
        http = new OkHttpClient();
        refresh(connection, cameras, photos, current);
        if (liveLimit > 0) {
            liveGrid = new ScreensaverLiveGrid(context, connection, cameras,
                    surfaces, photos, liveStatuses, liveLimit, decoder);
            liveGrid.start();
        }
    }

    /** The grid always stays muted; opening a chosen live tile enables only its audio. */
    private void showFullscreen(int slot, String entity) {
        if (stopped || fullscreenSlot >= 0 || gridPhotos == null
                || slot < 0 || slot >= gridPhotos.length) return;
        fullscreenSlot = slot;
        FrameLayout panel = new FrameLayout(context);
        panel.setBackgroundColor(Color.BLACK);
        ImageView photo = new ImageView(context);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setImageDrawable(gridPhotos[slot].getDrawable());
        panel.addView(photo, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fullscreenPhoto = photo;
        TextureView fullVideo = null;
        if (liveGrid != null && liveGrid.hasLiveSlot(slot)) {
            AspectRatioFrameLayout aspect = new AspectRatioFrameLayout(context);
            aspect.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            aspect.setAspectRatio(16f / 9f);
            FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            videoParams.gravity = Gravity.CENTER;
            panel.addView(aspect, videoParams);
            fullVideo = new TextureView(context);
            fullVideo.setOpaque(false);
            fullVideo.setAlpha(0f);
            aspect.addView(fullVideo, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        TextView cameraName = new TextView(context);
        cameraName.setText(label(entity));
        cameraName.setTextColor(Color.WHITE);
        cameraName.setTextSize(22);
        cameraName.setPadding(dp(12), dp(8), dp(12), dp(8));
        cameraName.setBackgroundColor(0xAA000000);
        FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        panel.addView(cameraName, nameParams);
        TextView audioLabel = new TextView(context);
        audioLabel.setText(fullVideo == null ? "Snapshot · no audio" : "Checking camera audio…");
        audioLabel.setTextColor(Color.WHITE);
        audioLabel.setTextSize(15);
        audioLabel.setPadding(dp(8), dp(5), dp(8), dp(5));
        audioLabel.setBackgroundColor(0xAA000000);
        FrameLayout.LayoutParams audioParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        panel.addView(audioLabel, audioParams);
        root.addView(panel, cardParams());
        fullscreenPanel = panel;

        if (liveGrid != null) liveGrid.selectFullscreen(slot, fullVideo, audioLabel);

        Button back = new Button(context);
        back.setText("Back to cameras");
        back.setTextColor(Color.WHITE);
        back.setTextSize(17);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setAllCaps(false);
        back.setBackgroundTintList(null);
        back.setBackground(controlBackground(0xFF174844, 0xFF177A65));
        back.setFocusable(true);
        back.setFocusableInTouchMode(true);
        back.setOnClickListener(v -> exitFullscreen());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(216), dp(52));
        backParams.gravity = Gravity.BOTTOM | Gravity.START;
        backParams.setMargins(dp(16), dp(16), dp(16), dp(16));
        root.addView(back, backParams);
        fullscreenBack = back;
        if (closeControl != null) {
            // Don't rely on spatial guessing between the two footer buttons.
            back.setId(View.generateViewId());
            closeControl.setId(View.generateViewId());
            back.setNextFocusRightId(closeControl.getId());
            closeControl.setNextFocusLeftId(back.getId());
        }
        back.post(() -> { if (!stopped && fullscreenSlot == slot) back.requestFocus(); });
    }

    private void exitFullscreen() {
        if (fullscreenSlot < 0) return;
        int slot = fullscreenSlot;
        // Mute and move the video surface back before destroying the full-screen view.
        if (liveGrid != null) liveGrid.clearFullscreen();
        fullscreenSlot = -1;
        if (fullscreenBack != null) root.removeView(fullscreenBack);
        if (fullscreenPanel != null) root.removeView(fullscreenPanel);
        fullscreenBack = null;
        fullscreenPanel = null;
        fullscreenPhoto = null;
        if (closeControl != null) closeControl.setNextFocusLeftId(View.NO_ID);
        if (!stopped && gridCells != null && slot < gridCells.length) {
            gridCells[slot].requestFocus();
        }
    }

    private void refresh(CredentialStore.Connection connection, String[] cameras,
            ImageView[] photos, int current) {
        if (stopped || current != generation) return;
        for (int i = 0; i < cameras.length; i++) {
            if (liveGrid == null || !liveGrid.isLive(i)) {
                fetch(connection, cameras[i], photos[i], current);
            }
        }
        main.postDelayed(() -> refresh(connection, cameras, photos, current), REFRESH_MS);
    }

    private void fetch(CredentialStore.Connection connection, String entity, ImageView photo, int current) {
        HttpUrl base = HttpUrl.parse(connection.url);
        if (base == null || photo == null) return;
        HttpUrl url = base.newBuilder().addPathSegment("api")
                .addPathSegment("camera_proxy").addPathSegment(entity).build();
        Request request = new Request.Builder().url(url)
                .header("Authorization", "Bearer " + connection.token)
                .header("Cache-Control", "no-cache").build();
        Call call = http.newCall(request);
        synchronized (calls) { calls.add(call); }
        call.enqueue(new Callback() {
            @Override public void onFailure(Call failed, java.io.IOException ignored) {
                synchronized (calls) { calls.remove(failed); }
            }
            @Override public void onResponse(Call completed, Response response) {
                synchronized (calls) { calls.remove(completed); }
                try (Response result = response) {
                    if (stopped || current != generation || !result.isSuccessful() || result.body() == null) return;
                    long declared = result.body().contentLength();
                    if (declared > MAX_SNAPSHOT_BYTES) return;
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = result.body().byteStream()) {
                        byte[] buf = new byte[8192];
                        int read;
                        while ((read = input.read(buf)) >= 0) {
                            if (bytes.size() + read > MAX_SNAPSHOT_BYTES) return;
                            bytes.write(buf, 0, read);
                        }
                    }
                    byte[] raw = bytes.toByteArray();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
                    int largest = Math.max(options.outWidth, options.outHeight);
                    options.inSampleSize = largest > 1280 ? 2 : 1;
                    options.inJustDecodeBounds = false;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
                    if (bitmap == null) return;
                    main.post(() -> {
                        if (!stopped && current == generation) {
                            photo.setImageBitmap(bitmap);
                            if (fullscreenSlot >= 0 && gridPhotos != null
                                    && gridPhotos[fullscreenSlot] == photo && fullscreenPhoto != null) {
                                fullscreenPhoto.setImageBitmap(bitmap);
                            }
                        } else bitmap.recycle();
                    });
                } catch (Exception ignored) { }
            }
        });
    }

    private CredentialStore.Connection signedIn() {
        try {
            return new CredentialStore(context).load();
        } catch (Exception ignored) {
            return null;
        }
    }

    private TextView showMessage(String text) {
        TextView message = new TextView(context);
        message.setText(text);
        message.setTextColor(Color.WHITE);
        message.setTextSize(22);
        message.setGravity(Gravity.CENTER);
        message.setBackgroundColor(Color.BLACK);
        message.setPadding(dp(32), dp(32), dp(32), dp(32));
        root.addView(message, cardParams());
        return message;
    }

    private FrameLayout.LayoutParams cardParams() {
        float fraction = ScreensaverDashboard.fraction(new OverlayPreferences(context).screensaverSize());
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = fraction >= 1f
                ? FrameLayout.LayoutParams.MATCH_PARENT
                : Math.max(dp(320), Math.round(metrics.widthPixels * fraction));
        int height = fraction >= 1f
                ? FrameLayout.LayoutParams.MATCH_PARENT
                : Math.max(dp(180), Math.round(metrics.heightPixels * fraction));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER;
        if (fraction >= 1f) params.bottomMargin = dp(72);
        root.setBackgroundColor(fraction >= 1f ? Color.BLACK : 0x00000000);
        return params;
    }

    private Button closeButton() {
        Button close = new Button(context);
        close.setText("Close");
        close.setTextColor(Color.WHITE);
        close.setTextSize(18);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setAllCaps(false);
        close.setBackgroundTintList(null);
        close.setBackground(controlBackground(0xFF1F6B4A, 0xFF19836B));
        close.setFocusable(true);
        close.setOnClickListener(v -> host.close());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(180), dp(52));
        // For HA, never cover login fields or dashboard controls in the bottom row.
        // Frigate/camera mode retains its already-tested footer placement.
        params.gravity = haDashboardPage
                ? Gravity.TOP | Gravity.START : Gravity.BOTTOM | Gravity.END;
        if (haDashboardPage) params.setMargins(dp(16), dp(16), dp(16), dp(16));
        else params.setMargins(dp(16), dp(16), dp(24), dp(16));
        close.setLayoutParams(params);
        if (firstGridCell == null) close.post(() -> close.requestFocus());
        return close;
    }

    /** Strong, theme-independent D-pad focus contrast on BOTH footer controls. */
    private StateListDrawable controlBackground(int normalColor, int focusedColor) {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(focusedColor);
        focused.setCornerRadius(dp(12));
        focused.setStroke(dp(4), 0xFFFFD166);
        GradientDrawable regular = new GradientDrawable();
        regular.setColor(normalColor);
        regular.setCornerRadius(dp(12));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_focused }, focused);
        states.addState(new int[] { android.R.attr.state_pressed }, focused);
        states.addState(new int[] {}, regular);
        return states;
    }

    private static String label(String entity) {
        return entity.startsWith("camera.") ? entity.substring("camera.".length()) : entity;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
