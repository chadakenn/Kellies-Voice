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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.ui.AspectRatioFrameLayout;

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

    private final Context context;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final List<Call> calls = new ArrayList<>();
    private OkHttpClient http;
    private WebView web;
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
        if (!handleBack()) host.close();
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
        if (!prefs.screensaverDashboard()) {
            showMessage("Frigate screensaver is off. Turn it on in Kellie's Voice settings, then press Close.");
            root.addView(closeButton());
            return;
        }
        String page = prefs.screensaverUrl();
        String[] cameras = ScreensaverDashboard.cameraList(prefs.screensaverCameras());
        if (!page.isEmpty()) {
            try {
                showPage(page);
            } catch (RuntimeException ignored) {
                showMessage("This TV couldn't open the Frigate page.");
            }
        } else if (cameras.length > 0) showCameras(cameras);
        else showMessage("No Frigate URL or HA cameras are saved on this TV.\n"
                + "If you selected cameras in Home Assistant, turn on Home Assistant layout "
                + "in Kellie's Voice settings, then check Screensaver status & permissions "
                + "and choose Resync HA cameras.");
        if (web != null) root.addView(browseButton());
        closeControl = closeButton();
        root.addView(closeControl);
        if (firstGridCell != null) firstGridCell.post(() -> {
            if (!stopped && fullscreenSlot < 0) firstGridCell.requestFocus();
        });
    }

    void stop() {
        stopped = true;
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
        if (web != null) {
            web.stopLoading();
            if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
    }

    private void showPage(String page) {
        web = new WebView(context);
        web.setBackgroundColor(Color.BLACK);
        WebSettings settings = web.getSettings();
        OverlayPreferences prefs = new OverlayPreferences(context);
        boolean desktop = prefs.screensaverDesktopPage();
        if (desktop) {
            // Frigate otherwise uses a phone-width WebView viewport on many Android TVs.
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUserAgentString(ScreensaverDashboard.desktopUserAgent(settings.getUserAgentString()));
        }
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setDomStorageEnabled(true);
        CookieManager cookies = CookieManager.getInstance();
        if (cookies != null) cookies.setAcceptThirdPartyCookies(web, false);
        web.setFocusableInTouchMode(true);
        web.setOnKeyListener((view, key, event) -> browsing && remoteKey(key, event));
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (desktop && !stopped && ScreensaverDashboard.sameSite(page, url)) {
                    view.evaluateJavascript(ScreensaverDashboard.desktopViewportScript(
                            ScreensaverDashboard.viewportWidth(prefs.screensaverFit())), null);
                }
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return true;
                return !ScreensaverDashboard.sameSite(page, request.getUrl().toString());
            }
        });
        FrameLayout.LayoutParams params = cardParams();
        // Let Frigate use the whole TV; Close floats over the bottom-right corner.
        if (params.width == FrameLayout.LayoutParams.MATCH_PARENT) params.bottomMargin = 0;
        root.addView(web, params);
        web.loadUrl(page);
    }

    /** Explicit remote browsing. WebView itself may not expose Frigate's tiles to D-pad focus. */
    private Button browseButton() {
        Button browse = new Button(context);
        browse.setText("Select camera");
        browse.setTextColor(Color.WHITE);
        browse.setTextSize(16);
        browse.setAllCaps(false);
        browse.setFocusable(true);
        browse.setOnClickListener(v -> startBrowsing());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(186), dp(52));
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.setMargins(dp(16), dp(16), dp(16), dp(16));
        browse.setLayoutParams(params);
        return browse;
    }

    private void startBrowsing() {
        if (web == null || stopped) return;
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

    private boolean remoteKey(int key, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
                cursor.move(ScreensaverRemoteCursor.UP); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cursor.move(ScreensaverRemoteCursor.RIGHT); break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                cursor.move(ScreensaverRemoteCursor.DOWN); break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                cursor.move(ScreensaverRemoteCursor.LEFT); break;
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
        return true;
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
        CredentialStore.Connection connection = signedIn();
        if (connection == null) {
            showMessage("Sign in to Home Assistant to load these cameras.");
            return;
        }
        http = new OkHttpClient();
        int current = generation;
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

    private void showMessage(String text) {
        TextView message = new TextView(context);
        message.setText(text);
        message.setTextColor(Color.WHITE);
        message.setTextSize(22);
        message.setGravity(Gravity.CENTER);
        message.setBackgroundColor(Color.BLACK);
        message.setPadding(dp(32), dp(32), dp(32), dp(32));
        root.addView(message, cardParams());
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
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(dp(16), dp(16), dp(24), dp(16));
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
