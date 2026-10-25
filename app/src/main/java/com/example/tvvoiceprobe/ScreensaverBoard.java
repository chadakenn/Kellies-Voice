package com.example.tvvoiceprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private int generation;
    private boolean stopped;
    private final Runnable onSettings = () -> main.post(() -> {
        if (!stopped) start();
    });

    ScreensaverBoard(Context context, Host host) {
        this.context = context;
        this.host = host;
        root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusable(true);
        root.setOnKeyListener((view, key, event) -> {
            if (key == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                host.close();
                return true;
            }
            return false;
        });
        ScreensaverSignals.listen(onSettings);
    }

    View view() { return root; }

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
        else showMessage("Set a Frigate page or camera list in Kellie's Voice settings.");
        root.addView(closeButton());
    }

    void stop() {
        stopped = true;
        generation++;
        ScreensaverSignals.unlisten(onSettings);
        main.removeCallbacksAndMessages(null);
        List<Call> pending;
        synchronized (calls) {
            pending = new ArrayList<>(calls);
            calls.clear();
        }
        for (Call call : pending) call.cancel();
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
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setDomStorageEnabled(true);
        CookieManager cookies = CookieManager.getInstance();
        if (cookies != null) cookies.setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return true;
                return !ScreensaverDashboard.sameSite(page, request.getUrl().toString());
            }
        });
        FrameLayout.LayoutParams params = cardParams();
        root.addView(web, params);
        web.loadUrl(page);
    }

    private void showCameras(String[] cameras) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(16), dp(16), dp(16), dp(72));
        grid.setBackgroundColor(Color.BLACK);
        ImageView[] photos = new ImageView[cameras.length];
        int rows = (cameras.length + 1) / 2;
        int index = 0;
        for (int row = 0; row < rows; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            for (int column = 0; column < 2 && index < cameras.length; column++) {
                FrameLayout cell = new FrameLayout(context);
                ImageView photo = new ImageView(context);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photo.setBackgroundColor(0xFF141414);
                cell.addView(photo, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
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
    }

    private void refresh(CredentialStore.Connection connection, String[] cameras,
            ImageView[] photos, int current) {
        if (stopped || current != generation) return;
        for (int i = 0; i < cameras.length; i++) fetch(connection, cameras[i], photos[i], current);
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
                        if (!stopped && current == generation) photo.setImageBitmap(bitmap);
                        else bitmap.recycle();
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
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF1F6B4A);
        shape.setCornerRadius(dp(12));
        close.setBackground(shape);
        close.setFocusable(true);
        close.setOnClickListener(v -> host.close());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(180), dp(52));
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(dp(16), dp(16), dp(24), dp(16));
        close.setLayoutParams(params);
        close.post(() -> close.requestFocus());
        return close;
    }

    private static String label(String entity) {
        return entity.startsWith("camera.") ? entity.substring("camera.".length()) : entity;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
