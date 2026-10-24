package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

/**
 * Android TV D-pad-first Home Assistant Assist UI with explicitly enabled
 * overlay prototype. No launcher replacement, wake word, or Frigate PiP.
 */
public class VoiceActivity extends Activity implements HaAssistClient.Events {
    private static final int MIC_PERMISSION_REQUEST = 17;
    private static final int OVERLAY_MIC_PERMISSION_REQUEST = 18;
    private static final int OVERLAY_SETTINGS_REQUEST = 19;
    private final List<Button> buttons = new ArrayList<>();
    private int selected = 0;
    private TextView status;
    private LinearLayout mainRoot;
    private TextView mainTitle;
    private TextView answerHeading;
    private TextView transcriptHeading;
    private TextView recognized;
    private TextView response;
    private TextView connection;
    private CredentialStore store;
    private HaAssistClient assist;
    private CredentialStore.Connection settings;
    private MediaPlayer player;
    private SpeechFocus speechFocus;
    private boolean playing;
    private boolean warningPending;
    private UpdateManager updater;
    private boolean manualUpdateCheck;
    private boolean transparentShortcut;
    private boolean initialConnectionRequested;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver overlayLaunchReceiver;
    private final Runnable overlayLaunchTimeout = () -> {
        stopListeningForOverlay();
        if (transparentShortcut) openNormalAppFromShortcut();
        else status("Overlay start timed out. Try again.");
    };

    @Override protected void onStart() {
        super.onStart();
        // Apply saved preferences on returning from another app or system screen.
        applyMainPalette();
        HaDiscovery.setWanted(this, new OverlayPreferences(this).haManage());
        HaDiscovery.acquire(this);
        if (assist != null) assist.setLayoutSync(new OverlayPreferences(this).haManage());
        // User opted in earlier: resume the receiver only when this Activity
        // is explicitly brought back. The service owns no microphone.
        if (!transparentShortcut && new OverlayPreferences(this).tvEvents() && settings != null
                && Settings.canDrawOverlays(this)) {
            try {
                startForegroundService(new Intent(this, TvEventsService.class)
                        .setAction(TvEventsService.ACTION_START));
                TvEventsRestartReceiver.clearResume(this);
            } catch (RuntimeException ignored) {
                TvEventsRecovery.note(TvEventsRecovery.STOPPED);
            }
        }
        // Reuse saved credentials without asking for a manual Reconnect click.
        // No microphone is activated here, just the authenticated socket.
        if (transparentShortcut || assist == null || settings == null) return;
        if (!initialConnectionRequested) {
            initialConnectionRequested = true;
            assist.connect(settings.url, settings.token);
        } else {
            assist.ensureConnected();
        }
    }

    @Override public void onCreate(Bundle state) {
        // The onn ⭐ picker starts the package's main Activity. When its
        // explicit overlay fallback is enabled, draw no full-screen UI at all.
        // Changing the window theme before super.onCreate avoids the flash of
        // the ordinary TV screen during the service's foreground startup.
        boolean shortcut = shouldLaunchOverlayFromLauncher(getIntent());
        if (shortcut) setTheme(R.style.KellieVoiceShortcutTransparentTheme);
        super.onCreate(state);
        if (shortcut && canStartOverlayImmediately()) {
            transparentShortcut = true;
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            View transparentWindow = new View(this);
            setContentView(transparentWindow);
            transparentWindow.post(this::startTransparentShortcutOverlay);
            return;
        }
        store = new CredentialStore(this);
        assist = new HaAssistClient(this, this);
        updater = new UpdateManager(this, new UpdateManager.Listener() {
            @Override public void onUpToDate(String message) {
                rememberUpdateCheck();
                if (manualUpdateCheck) status(message);
                manualUpdateCheck = false;
            }
            @Override public void onAvailable(String version, String notes) {
                rememberUpdateCheck();
                manualUpdateCheck = false;
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(VoiceActivity.this)
                        .setTitle("Kellie's Voice update available: " + version)
                        .setMessage("Download the signed update from GitHub Releases?\n\n" +
                                (notes == null ? "" : notes.substring(0, Math.min(notes.length(), 700))))
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Download update", (dialog, choice) -> {
                            status("Downloading verified update...");
                            updater.download();
                        })
                        .show();
            }
            @Override public void onProgress(String message) { status(message); }
            @Override public void onDownloaded() {
                status("Update verified. Ready to install.");
                promptInstallUpdate();
            }
            @Override public void onError(String message) {
                if (manualUpdateCheck) status(message);
                else if (!message.contains("No public GitHub Release")) status("Updater: " + message);
                manualUpdateCheck = false;
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(34, 22, 34, 22);
        mainRoot = root;
        AppPalette palette = currentPalette();
        root.setBackgroundColor(palette.screen);

        mainTitle = label("KELLIE'S VOICE   ✦   HOME ASSISTANT", 24, palette.accent);
        root.addView(mainTitle);

        connection = label("Home Assistant: not configured", 15, palette.muted);
        root.addView(connection);

        status = label("Connect, then press TALK.", 17, palette.accent);
        root.addView(status);

        // Prioritize the conversation on small TV screens. The large seven-button
        // diagnostic layout hid all but the first line of the recognized speech.
        ScrollView scroller = new ScrollView(this);
        scroller.setFocusable(false);
        scroller.setFillViewport(true);
        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        history.setPadding(10, 6, 10, 6);

        answerHeading = label("HOME ASSISTANT SAYS", 16, palette.muted);
        history.addView(answerHeading);
        response = label("Your answer will appear here.", 25, palette.text);
        history.addView(response);

        transcriptHeading = label("YOU SAID", 16, palette.muted);
        history.addView(transcriptHeading);
        recognized = label("Nothing yet", 21, palette.text);
        history.addView(recognized);

        scroller.addView(history);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        // Three controls in one bottom row leave most of the screen for the
        // actual Home Assistant response. The other actions live in SETTINGS.
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        addButton(toolbar, "TALK", v -> startTalk());
        addButton(toolbar, "STOP", v -> assist.finishTalking());
        addButton(toolbar, "SETTINGS", v -> showSettingsMenu());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        // A short, focus-safe welcome view on ordinary full-screen launches.
        // Keep the existing root ready underneath, so HA configuration and
        // microphone behavior remain untouched.
        FrameLayout page = new FrameLayout(this);
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));
        KellieSplashView splash = new KellieSplashView(this);
        page.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(page);
        try { settings = store.load(); }
        catch (Exception e) { status("Saved connection couldn't be decrypted. Configure it again."); }
        updateConnectionLabel();
        if (!buttons.isEmpty()) buttons.get(0).post(() -> focus(0));
        // Some onn firmware shows only one entry per package in the ⭐ picker.
        // Launchers may strip CATEGORY_LAUNCHER/LEANBACK_LAUNCHER on the actual
        // start intent, so the explicit user opt-in + ACTION_MAIN is sufficient.
        if (shouldLaunchOverlayFromLauncher(getIntent())) {
            // ⭐ shortcuts should never be slowed by the splash.
            page.removeView(splash);
            root.post(this::launchOverlay);
        } else {
            splash.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                splash.animate().alpha(0f).setDuration(260)
                        .withEndAction(() -> page.removeView(splash)).start();
            }, 1050L);
            long now = System.currentTimeMillis();
            android.content.SharedPreferences checks =
                    getSharedPreferences("tv_voice_update_checks", MODE_PRIVATE);
            if (now - checks.getLong("last_checked", 0) >= 24L * 60 * 60 * 1000) {
                root.post(() -> updater.check());
            }
        }
    }

    private void rememberUpdateCheck() {
        getSharedPreferences("tv_voice_update_checks", MODE_PRIVATE)
                .edit().putLong("last_checked", System.currentTimeMillis()).apply();
    }

    private boolean canStartOverlayImmediately() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED || !Settings.canDrawOverlays(this)) {
            return false;
        }
        try {
            return new CredentialStore(this).load() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void startTransparentShortcutOverlay() {
        if (isFinishing() || isDestroyed()) return;
        try {
            // Start from the foreground Activity before returning to the
            // video; the service promotes itself to microphone foreground.
            listenForOverlayThenBackground();
            startForegroundService(new Intent(this, VoiceOverlayService.class));
        } catch (Exception ignored) {
            // On restricted firmware, recover to the real UI instead of
            // leaving the user with an invisible application.
            openNormalAppFromShortcut();
        }
    }

    private void openNormalAppFromShortcut() {
        stopListeningForOverlay();
        // A singleTask launch can reuse this same Activity; do not finish it.
        Intent open = new Intent(this, VoiceActivity.class);
        open.setAction("com.example.tvvoiceprobe.OPEN_SETTINGS");
        setIntent(open);
        transparentShortcut = false;
        recreate();
    }

    private boolean shouldLaunchOverlayFromLauncher(Intent intent) {
        // OPEN_SETTINGS and ordinary explicit activities must open the full UI.
        // The opt-in also prevents microphone startup on an ordinary install.
        return intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && new OverlayPreferences(this).mainLauncherOpensOverlay();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // If Android reuses a running instance, onCreate is not called.
        // Handle a new launcher/⭐ command without requiring a fresh Activity.
        if (transparentShortcut) {
            if (shouldLaunchOverlayFromLauncher(intent)) {
                getWindow().getDecorView().post(this::startTransparentShortcutOverlay);
            } else {
                openNormalAppFromShortcut();
            }
        } else if (shouldLaunchOverlayFromLauncher(intent)) {
            getWindow().getDecorView().post(this::launchOverlay);
        }
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 7, 0, 7);
        t.setFocusable(false);
        return t;
    }

    private AppPalette currentPalette() {
        return AppPalette.forSkin(new OverlayPreferences(this).skin());
    }

    /** Repaint the existing screen in place, preserving text, focus and Assist. */
    private void applyMainPalette() {
        if (mainRoot == null || transparentShortcut) return;
        AppPalette colors = currentPalette();
        mainRoot.setBackgroundColor(colors.screen);
        if (mainTitle != null) mainTitle.setTextColor(colors.accent);
        if (connection != null) connection.setTextColor(colors.muted);
        if (status != null) status.setTextColor(colors.accent);
        if (answerHeading != null) answerHeading.setTextColor(colors.muted);
        if (transcriptHeading != null) transcriptHeading.setTextColor(colors.muted);
        if (response != null) response.setTextColor(colors.text);
        if (recognized != null) recognized.setTextColor(colors.text);
        for (Button b : buttons) {
            b.setTextColor(colors.text);
            b.setBackgroundTintList(ColorStateList.valueOf(b.hasFocus() ? colors.focus : colors.button));
        }
    }

    private void addButton(LinearLayout parent, String value, View.OnClickListener action) {
        Button b = new Button(this);
        int index = buttons.size();
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(currentPalette().text);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setBackgroundTintList(ColorStateList.valueOf(currentPalette().button));
        b.setOnClickListener(action);
        b.setOnFocusChangeListener((v, focused) -> {
            if (focused) selected = index;
            AppPalette colors = currentPalette();
            b.setBackgroundTintList(ColorStateList.valueOf(focused ? colors.focus : colors.button));
        });
        buttons.add(b);
        parent.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private void focus(int index) {
        if (buttons.isEmpty()) return;
        selected = Math.max(0, Math.min(index, buttons.size() - 1));
        buttons.get(selected).requestFocus();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !buttons.isEmpty()) {
            View now = getCurrentFocus();
            if (!(now instanceof Button) || !buttons.contains(now)) {
                buttons.get(selected).post(() -> focus(selected));
            }
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focus(selected + 1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT) {
                focus(selected - 1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A) {
                if (!buttons.isEmpty()) buttons.get(selected).performClick();
                return true;
            }
        }
        // Ignore key release for navigation/selection to avoid duplicate activation.
        if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void updateConnectionLabel() {
        if (connection == null) return;
        connection.setText(settings == null
                ? "Home Assistant: not configured"
                : "Home Assistant: " + settings.url + (assist.isConnected() ? "  ✓ connected" : "  (not connected)"));
    }

    /**
     * Same copper-and-charcoal visual language as the TV overlay.
     * All menus are navigable with the onn D-pad and remain local-only.
     */
    private void showCopperMenu(String heading, String subtitle,
            String[] entries, java.util.function.IntConsumer onSelect) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        AppPalette colors = currentPalette();
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {colors.menuStart, colors.menuEnd});
        background.setCornerRadius(pad);
        background.setStroke(Math.max(1, pad / 16), colors.stroke);
        body.setBackground(background);
        TextView title = label(heading, 25, colors.accent);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(title);
        TextView hint = label(subtitle, 14, colors.muted);
        hint.setPadding(0, 0, 0, pad / 2);
        body.addView(hint);
        Button first = null;
        for (int i = 0; i < entries.length; i++) {
            final int item = i;
            Button option = copperMenuButton(entries[i]);
            option.setOnClickListener(v -> {
                dialog.dismiss();
                onSelect.accept(item);
            });
            LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
            row.bottomMargin = pad / 4;
            body.addView(option, row);
            if (first == null) first = option;
        }
        Button done = copperMenuButton("←  Back / Done");
        done.setOnClickListener(v -> dialog.dismiss());
        body.addView(done, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(body);
        dialog.setView(scroll, 0, 0, 0, 0);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int dpWidth = Math.round(720 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setLayout(Math.min(dpWidth, screenWidth - pad * 2),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        if (first != null) first.requestFocus();
    }

    private Button copperMenuButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(currentPalette().text);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(22, 10, 22, 10);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setBackground(menuRowBackground(false));
        b.setOnFocusChangeListener((v, focused) ->
                b.setBackground(menuRowBackground(focused)));
        return b;
    }

    private GradientDrawable menuRowBackground(boolean focused) {
        AppPalette colors = currentPalette();
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(focused ? colors.menuFocus : colors.menuButton);
        int dp = Math.round(1 * getResources().getDisplayMetrics().density);
        shape.setCornerRadius(Math.max(12, dp * 12));
        shape.setStroke(Math.max(1, dp), colors.stroke);
        return shape;
    }

    private void showSettingsMenu() {
        String[] entries = new String[] {
                "Home Assistant connection",
                "Connect / reconnect",
                "Start new conversation",
                "Hardware microphone diagnostics",
                "Microphone sensitivity: " + OverlayPreferences.noiseLabel(
                        new OverlayPreferences(this).noise()),
                "Mic input detection: " + (new OverlayPreferences(this).allowUnverifiedMic()
                        ? "Override ON" : "Safe checks ON"),
                "Open floating voice overlay",
                "Overlay appearance & position",
                "Home Assistant layout: " + (new OverlayPreferences(this).haManage() ? "ON" : "OFF"),
                "TV announcements & cameras: " + (new OverlayPreferences(this).tvEvents() ? "ON" : "OFF"),
                "TV alert status",
                "Live camera: " + LiveCameraMode.label(new OverlayPreferences(this).cameraMode()),
                "Check for app updates",
                "Install downloaded update",
                "Forget saved connection"
        };
        showCopperMenu("KELLIE'S VOICE  ✦  SETTINGS",
                "Warm, simple controls for your TV. Choose with the remote.", entries,
                which -> {
                    switch (which) {
                        case 0: configure(); break;
                        case 1: connect(); break;
                        case 2: newConversation(); break;
                        case 3: startActivity(new Intent(this, MainActivity.class)); break;
                        case 4: chooseMicSensitivity(); break;
                        case 5: chooseMicrophoneDetection(); break;
                        case 6: launchOverlay(); break;
                        case 7: showOverlayPreferences(); break;
                        case 8: chooseHaLayout(); break;
                        case 9: chooseTvNotifications(); break;
                        case 10: showTvAlertStatus(); break;
                        case 11: chooseCameraMode(); break;
                        case 12:
                            manualUpdateCheck = true;
                            status("Checking GitHub Releases...");
                            updater.check();
                            break;
                        case 13: promptInstallUpdate(); break;
                        case 14: confirmForget(); break;
                        default: break;
                    }
                });
    }

    private void chooseHaLayout() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean on = prefs.haManage();
        showCopperMenu("SETTINGS  ✦  HOME ASSISTANT",
                "Lets Home Assistant change position, size, style, auto-hide, and auto-listen. It cannot start the microphone or change the login. Leave this app open, then click Add on the discovered TV.",
                new String[] {
                        on ? "Turn Home Assistant layout OFF" : "Turn Home Assistant layout ON",
                        "Show this TV's pairing ID",
                        "Back to Settings"
                }, which -> {
                    if (which == 0) {
                        prefs.setHaManage(!on);
                        HaDiscovery.setWanted(this, prefs.haManage());
                        if (assist != null) assist.setLayoutSync(prefs.haManage());
                        status(prefs.haManage()
                                ? "Layout ON. Use Discovered in HA, or pair with this TV's ID."
                                : "Home Assistant layout control off.");
                    }
                    if (which == 1) {
                        new AlertDialog.Builder(this)
                                .setTitle("Kellie's Voice - TV pairing ID")
                                .setMessage("In Home Assistant, choose Settings > Devices & services"
                                        + " > Add integration > Kellie's Voice. Enter this ID:"
                                        + "\n\n" + prefs.deviceId()
                                        + "\n\nTurn Home Assistant layout ON on this TV"
                                        + " to enable remote layout updates."
                                        + "\n\nThis ID is not a password or your HA token.")
                                .setPositiveButton("Back to settings", (d, w) -> showSettingsMenu())
                                .show();
                        return;
                    }
                    showSettingsMenu();
                });
    }

    private void chooseTvNotifications() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean enabled = prefs.tvEvents();
        showCopperMenu("SETTINGS  ✦  TV ALERTS",
                "Opt-in announcements and Frigate camera snapshots over TV. No microphone is used. "
                        + "If alerts were already on, the TV tries to resume them after an update or reboot. "
                        + "That can still be blocked by Android.",
                new String[] {
                        enabled ? "Turn TV notifications OFF" : "Turn TV notifications ON",
                        "Preview announcement",
                        "Back to Settings"
                }, which -> {
                    if (which == 0) {
                        if (enabled) {
                            prefs.setTvEvents(false);
                            stopService(new Intent(this, TvEventsService.class));
                            status("TV announcements and camera popups disabled.");
                        } else if (settings == null) {
                            status("Configure Home Assistant before enabling TV notifications.");
                        } else if (!Settings.canDrawOverlays(this)) {
                            status("Grant 'Display over other apps' before enabling TV alerts.");
                            new AlertDialog.Builder(this)
                                    .setTitle("Allow TV announcement popups")
                                    .setMessage("Android must allow Kellie's Voice to display overlays over video. "
                                            + "Return here to enable alerts afterward.")
                                    .setNegativeButton("Later", null)
                                    .setPositiveButton("Open permission", (d, w) -> {
                                        try {
                                            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:" + getPackageName())));
                                        } catch (Exception ignored) {
                                            status("Could not open Android's overlay permission screen.");
                                        }
                                    }).show();
                        } else {
                            prefs.setTvEvents(true);
                            try {
                                startForegroundService(new Intent(this, TvEventsService.class)
                                        .setAction(TvEventsService.ACTION_START));
                                status("TV alerts enabled. Listening for Home Assistant events.");
                            } catch (RuntimeException ignored) {
                                prefs.setTvEvents(false);
                                status("Android couldn't start the TV alert receiver.");
                            }
                        }
                    } else if (which == 1) {
                        if (prefs.tvEvents()) {
                            try {
                                startForegroundService(new Intent(this, TvEventsService.class)
                                        .setAction(TvEventsService.ACTION_PREVIEW));
                                status("Previewing the announcement overlay.");
                            } catch (RuntimeException ignored) {
                                status("Couldn't open the preview. Check Android overlay permission.");
                            }
                        } else {
                            status("Turn on TV notifications to preview them.");
                        }
                    }
                    showSettingsMenu();
                });
    }

    private void chooseCameraMode() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        String[] modes = {LiveCameraMode.SOFTWARE, LiveCameraMode.SNAPSHOT, LiveCameraMode.HARDWARE};
        String[] choices = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            choices[i] = (modes[i].equals(prefs.cameraMode()) ? "✓  " : "     ")
                    + LiveCameraMode.label(modes[i]);
        }
        showCopperMenu("TV ALERTS  ✦  LIVE CAMERA",
                "Safe software avoids taking the video decoder from HDMI or streaming apps. "
                        + "A low-resolution H.264 camera stream works best. "
                        + "Unsupported software decoding falls back to a still snapshot.",
                choices, which -> {
                    String selected = modes[which];
                    if (LiveCameraMode.HARDWARE.equals(selected)) {
                        new AlertDialog.Builder(this)
                                .setTitle("Enable hardware camera decoder?")
                                .setMessage("Only choose this on a TV box that you have tested. "
                                        + "On integrated Android TVs, hardware decoding may blank "
                                        + "HDMI or interrupt Netflix and other playback, even "
                                        + "after the camera popup closes.")
                                .setNegativeButton("Cancel", (dialog, w) -> showSettingsMenu())
                                .setPositiveButton("Enable hardware", (dialog, w) -> {
                                    prefs.setCameraMode(LiveCameraMode.HARDWARE);
                                    status("Hardware camera decoding enabled on this TV.");
                                    showSettingsMenu();
                                }).show();
                    } else {
                        prefs.setCameraMode(selected);
                        status("Live camera: " + LiveCameraMode.label(selected));
                        showSettingsMenu();
                    }
                });
    }

    private void showTvAlertStatus() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean signedIn = settings != null;
        String message = TvEventsRecovery.describe(prefs.tvEvents(),
                Settings.canDrawOverlays(this), signedIn, TvEventsRecovery.runtime());
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("TV alert status")
                .setMessage(message + "\n\nThis does not start the microphone. "
                        + "A Home Assistant drop reconnects on its own while the receiver is running. "
                        + "Events from the gap are not shown later.")
                .setNegativeButton("Back", (d, w) -> showSettingsMenu());
        if (prefs.tvEvents() && signedIn && Settings.canDrawOverlays(this)) {
            dialog.setPositiveButton("Resume TV alerts", (d, w) -> {
                try {
                    startForegroundService(new Intent(this, TvEventsService.class)
                            .setAction(TvEventsService.ACTION_RESUME));
                    TvEventsRestartReceiver.clearResume(this);
                    status("Refreshing the TV alert connection.");
                } catch (RuntimeException ignored) {
                    TvEventsRecovery.note(TvEventsRecovery.STOPPED);
                    status("Android blocked the TV alert receiver. Try again from this screen.");
                }
                showSettingsMenu();
            });
        }
        dialog.show();
    }

    private void pushLayout(String setting) {
        if (assist != null && new OverlayPreferences(this).haManage()) {
            assist.publishLayout(setting);
        }
    }

    private void chooseMicrophoneDetection() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean bypass = prefs.allowUnverifiedMic();
        showCopperMenu("SETTINGS  ✦  MICROPHONE ROUTING",
                "Safe checks prevent TALK from recording TV audio instead of speech. "
                        + "Some Android TVs expose only a remote submix, not a mic.",
                new String[] {
                        bypass ? "Turn compatibility override OFF (recommended)"
                                : "Try unverified microphone (compatibility override)",
                        "Open microphone diagnostics",
                        "Back to Settings"
                }, which -> {
                    if (which == 0) {
                        if (bypass) {
                            prefs.setAllowUnverifiedMic(false);
                            status("Microphone input checks enabled.");
                            showSettingsMenu();
                        } else {
                            new AlertDialog.Builder(this)
                                    .setTitle("Try microphone anyway?")
                                    .setMessage("Only use this if your TV's audio-device list is wrong. "
                                            + "It cannot unlock Google/Alexa remote microphones. "
                                            + "An unusable input may still record silence; "
                                            + "turn this off after testing.")
                                    .setNegativeButton("Cancel", (dialog, w) -> showSettingsMenu())
                                    .setPositiveButton("Try anyway", (dialog, w) -> {
                                        prefs.setAllowUnverifiedMic(true);
                                        status("Microphone route check overridden for diagnostics.");
                                        showSettingsMenu();
                                    }).show();
                        }
                    } else if (which == 1) {
                        startActivity(new Intent(this, MainActivity.class));
                    } else {
                        showSettingsMenu();
                    }
                });
    }

    private void chooseMicSensitivity() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        final String[] values = {
                OverlayPreferences.NOISE_QUIET, OverlayPreferences.NOISE_NORMAL,
                OverlayPreferences.NOISE_TV
        };
        String[] options = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            options[i] = (values[i].equals(prefs.noise()) ? "✓  " : "     ")
                    + OverlayPreferences.noiseLabel(values[i]);
        }
        showCopperMenu("SETTINGS  ✦  MICROPHONE",
                "Controls the automatic pause detector. STOP still ends listening.",
                options, which -> {
                    prefs.setNoise(values[which]);
                    pushLayout(HaLayout.NOISE);
                    status("Microphone sensitivity: " + OverlayPreferences.noiseLabel(prefs.noise()));
                    showSettingsMenu();
                });
    }

    private void promptInstallUpdate() {
        if (isFinishing() || isDestroyed()) return;
        if (!updater.downloadedExists()) {
            status("No downloaded update. First choose Check for app updates.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Install verified Kellie's Voice update?")
                .setMessage("Android will ask you to approve the package update. " +
                        "Your Home Assistant login and preferences should be retained " +
                        "because the release uses our permanent signing key.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Open Android installer", (dialog, which) -> updater.install())
                .show();
    }

    private void showOverlayPreferences() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        String[] options = {
                "Position: " + OverlayPreferences.positionLabel(prefs.position()),
                "Size: " + OverlayPreferences.sizeLabel(prefs.size()),
                "Auto-hide after answer: " + prefs.hideLabel(),
                "Appearance: " + OverlayPreferences.skinLabel(prefs.skin()),
                "Auto-listen on launch: " + (prefs.autoListen() ? "ON" : "OFF"),
                "⭐ picker fallback: " + (prefs.mainLauncherOpensOverlay() ? "ON" : "OFF")
        };
        showCopperMenu("OVERLAY  ✦  APPEARANCE",
                "Changes save to this TV. Close and reopen the overlay to see layout changes.",
                options, index -> {
                    switch (index) {
                        case 0: chooseOverlayPosition(prefs); break;
                        case 1: chooseOverlaySize(prefs); break;
                        case 2: chooseOverlayHide(prefs); break;
                        case 3: chooseOverlaySkin(prefs); break;
                        case 4:
                            prefs.setAutoListen(!prefs.autoListen());
                            pushLayout(HaLayout.AUTO_LISTEN);
                            status("Auto-listen: " + (prefs.autoListen() ? "ON" : "OFF"));
                            showOverlayPreferences();
                            break;
                        case 5:
                            showCopperMenu("⭐ REMOTE SHORTCUT",
                                    "Enable only when onn lists Kellie's Voice but not the separate Overlay shortcut. The overlay SETTINGS button always opens this screen.",
                                    new String[] {
                                            prefs.mainLauncherOpensOverlay()
                                                    ? "Turn fallback OFF" : "Turn fallback ON",
                                            "Keep current setting"
                                    }, which -> {
                                        if (which == 0) {
                                            prefs.setMainLauncherOpensOverlay(
                                                    !prefs.mainLauncherOpensOverlay());
                                            status("⭐ fallback: " +
                                                    (prefs.mainLauncherOpensOverlay() ? "ON" : "OFF"));
                                        }
                                        showOverlayPreferences();
                                    });
                            break;
                        default: break;
                    }
                });
    }

    private void chooseOverlayPosition(OverlayPreferences prefs) {
        final String[] positions = {
                OverlayPreferences.POSITION_DEFAULT,
                OverlayPreferences.POSITION_TOP_LEFT,
                OverlayPreferences.POSITION_TOP_RIGHT,
                OverlayPreferences.POSITION_BOTTOM_LEFT,
                OverlayPreferences.POSITION_BOTTOM_RIGHT,
                OverlayPreferences.POSITION_CENTER,
                OverlayPreferences.POSITION_BOTTOM_CENTER
        };
        String[] labels = new String[positions.length];
        for (int i = 0; i < positions.length; i++) {
            labels[i] = (positions[i].equals(prefs.position()) ? "✓  " : "     ")
                    + OverlayPreferences.positionLabel(positions[i]);
        }
        showCopperMenu("OVERLAY  ✦  POSITION",
                "Choose where the floating voice card appears.", labels, which -> {
                    prefs.setPosition(positions[which]);
                    pushLayout(HaLayout.POSITION);
                    status("Overlay position saved. Reopen the overlay to apply.");
                    showOverlayPreferences();
                });
    }

    private void chooseOverlaySize(OverlayPreferences prefs) {
        final String[] values = {
                OverlayPreferences.SIZE_SMALL,
                OverlayPreferences.SIZE_MEDIUM,
                OverlayPreferences.SIZE_LARGE
        };
        String[] options = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            options[i] = (values[i].equals(prefs.size()) ? "✓  " : "     ")
                    + OverlayPreferences.sizeLabel(values[i]);
        }
        showCopperMenu("OVERLAY  ✦  SIZE",
                "Small saves screen space. Large makes text easier to read.", options, which -> {
                    prefs.setSize(values[which]);
                    pushLayout(HaLayout.SIZE);
                    status("Overlay size saved. Reopen the overlay to apply.");
                    showOverlayPreferences();
                });
    }

    private void chooseOverlayHide(OverlayPreferences prefs) {
        final int[] values = {5, 7, 10, 20, 0};
        String[] options = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String label = values[i] == 0 ? "Never (manual close)"
                    : values[i] + " seconds";
            if (values[i] == 7) label += " (original)";
            options[i] = ((prefs.autoHide() && values[i] == prefs.hideSeconds())
                    || (!prefs.autoHide() && values[i] == 0) ? "✓  " : "     ") + label;
        }
        showCopperMenu("OVERLAY  ✦  AUTO-HIDE",
                "Starts counting after Kellie's spoken reply finishes.", options, which -> {
                    prefs.setHideSeconds(values[which]);
                    pushLayout(HaLayout.AUTO_HIDE);
                    status("Auto-hide saved: " + prefs.hideLabel());
                    showOverlayPreferences();
                });
    }

    private void chooseOverlaySkin(OverlayPreferences prefs) {
        final String[] values = {
                OverlayPreferences.SKIN_GLASS,
                OverlayPreferences.SKIN_CLASSIC,
                OverlayPreferences.SKIN_SIGNAL,
                OverlayPreferences.SKIN_AURORA
        };
        String[] labels = {
                "Copper Glass (rounded, animated)",
                "Compact Charcoal (smaller text)",
                "Night Signal (warm ink, brass bar meter)",
                "Aurora Glass (blue light, frosted midnight)"
        };
        for (int i = 0; i < labels.length; i++) {
            if (values[i].equals(prefs.skin())) labels[i] = "✓  " + labels[i];
            else labels[i] = "     " + labels[i];
        }
        showCopperMenu("OVERLAY  ✦  STYLE",
                "Choose Copper Glass, Compact Charcoal, Night Signal, or Aurora Glass. Reopen the overlay to apply.",
                labels, which -> {
                    prefs.setSkin(values[which]);
                    applyMainPalette();
                    pushLayout(HaLayout.STYLE);
                    status("Appearance saved: " + OverlayPreferences.skinLabel(prefs.skin()));
                    showOverlayPreferences();
                });
    }

    private void launchOverlay() {
        // Never activate the overlay without explicit user action and consent.
        if (settings == null) {
            status("Configure Home Assistant before opening the overlay.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, OVERLAY_MIC_PERMISSION_REQUEST);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            status("Grant 'Display over other apps', then start the overlay again.");
            new AlertDialog.Builder(this)
                    .setTitle("Allow Kellie's Voice overlay")
                    .setMessage("Google TV must allow this app to display over other apps. " +
                            "The microphone listens after launch when Auto-listen is enabled. " +
                            "The overlay stops completely when you choose CLOSE or BACK. " +
                            "If the TV has no overlay permission screen, this firmware may not support it.")
                    .setNegativeButton("Not now", null)
                    .setPositiveButton("Open system permission", (dialog, which) -> {
                        try {
                            startActivityForResult(new Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName())), OVERLAY_SETTINGS_REQUEST);
                        } catch (Exception error) {
                            status("No overlay permission screen found on this Google TV firmware.");
                        }
                    }).show();
            return;
        }
        try {
            Intent start = new Intent(this, VoiceOverlayService.class);
            listenForOverlayThenBackground();
            startForegroundService(start);
            status("Starting voice overlay... CLOSE or BACK dismisses it.");
        } catch (Exception error) {
            stopListeningForOverlay();
            status("Couldn't start overlay on this Google TV firmware.");
        }
    }

    private void listenForOverlayThenBackground() {
        stopListeningForOverlay();
        overlayLaunchReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                boolean failed = VoiceOverlayService.ACTION_OVERLAY_FAILED.equals(intent.getAction());
                stopListeningForOverlay();
                if (failed) {
                    if (transparentShortcut) openNormalAppFromShortcut();
                    else status("Couldn't start the overlay. Check connection and permissions.");
                } else if (!isFinishing() && !isDestroyed()) {
                    moveTaskToBack(true);
                }
            }
        };
        IntentFilter filter = new IntentFilter(VoiceOverlayService.ACTION_OVERLAY_READY);
        filter.addAction(VoiceOverlayService.ACTION_OVERLAY_FAILED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(overlayLaunchReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(overlayLaunchReceiver, filter);
        }
        mainHandler.postDelayed(overlayLaunchTimeout, 8000L);
    }

    private void stopListeningForOverlay() {
        mainHandler.removeCallbacks(overlayLaunchTimeout);
        if (overlayLaunchReceiver != null) {
            try { unregisterReceiver(overlayLaunchReceiver); } catch (Exception ignored) { }
            overlayLaunchReceiver = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_SETTINGS_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                status("Overlay allowed! Open SETTINGS and choose floating overlay again.");
            } else {
                status("Overlay permission wasn't granted. The Kellie's Voice app still works.");
            }
        }
    }

    private void configure() {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(30, 10, 30, 10);
        form.setOrientation(LinearLayout.VERTICAL);

        form.addView(label("Home Assistant URL (prefer HTTPS / Nabu Casa)", 16, 0xff221713));
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://your-ha-address");
        url.setText(settings == null ? "" : settings.url);
        form.addView(url);

        form.addView(label("Long-lived access token (never saved in GitHub)", 16, 0xff221713));
        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint(settings == null ? "Paste token" : "Leave blank to keep existing token");
        form.addView(token);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect to Home Assistant");
        builder.setView(form);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Save and connect", (dialog, which) -> saveAndConnect(
                url.getText().toString().trim(), token.getText().toString().trim()));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> url.requestFocus());
        dialog.show();
    }

    private void saveAndConnect(String url, String token) {
        if (settings != null && token.isEmpty()) token = settings.token;
        if (token.isEmpty()) { status("Enter a Home Assistant long-lived access token."); return; }
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || (!"https".equals(parsed.scheme()) && !"http".equals(parsed.scheme()))) {
            status("Enter the full http:// or https:// Home Assistant address."); return;
        }
        final String acceptedToken = token;
        if ("http".equals(parsed.scheme())) {
            new AlertDialog.Builder(this)
                    .setTitle("HTTP connection is not encrypted")
                    .setMessage("Your Home Assistant token and voice audio would travel without transport encryption. " +
                            "Use HTTPS when possible. Continue with HTTP only on a trusted private network.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Use HTTP locally", (d, w) -> persistAndConnect(url, acceptedToken))
                    .show();
            return;
        }
        persistAndConnect(url, acceptedToken);
    }

    private void persistAndConnect(String url, String token) {
        try {
            store.save(url, token);
            settings = store.load();
            updateConnectionLabel();
            if (new OverlayPreferences(this).tvEvents()) {
                stopService(new Intent(this, TvEventsService.class));
                try {
                    startForegroundService(new Intent(this, TvEventsService.class)
                            .setAction(TvEventsService.ACTION_START));
                } catch (RuntimeException ignored) { }
            }
            connect();
        } catch (Exception e) {
            status("Couldn't safely save connection. Try again.");
        }
    }

    private void connect() {
        if (settings == null) { configure(); return; }
        initialConnectionRequested = true;
        assist.connect(settings.url, settings.token);
        updateConnectionLabel();
    }

    private void startTalk() {
        if (assist != null && !assist.isConnected()) {
            assist.ensureConnected();
            status("Reconnecting to Home Assistant. Try TALK when connected.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST);
        } else {
            recognized.setText("Checking microphone...");
            response.setText("Waiting for Home Assistant...");
            if (player != null) { player.release(); player = null; }
            if (speechFocus != null) speechFocus.release();
            assist.setRoomNoise(new OverlayPreferences(this).noise());
            assist.talk();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == MIC_PERMISSION_REQUEST) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startTalk();
            else status("Microphone permission denied.");
        } else if (requestCode == OVERLAY_MIC_PERMISSION_REQUEST) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) launchOverlay();
            else status("Microphone permission denied; overlay not started.");
        }
    }

    private void newConversation() {
        if (player != null) { player.release(); player = null; }
        if (speechFocus != null) speechFocus.release();
        assist.disconnect();
        recognized.setText("Nothing yet");
        response.setText("Ready for a fresh conversation.");
        if (settings != null) {
            connect();
        } else {
            status("Conversation cleared. Configure Home Assistant to continue.");
        }
    }

    private void confirmForget() {
        new AlertDialog.Builder(this)
                .setTitle("Forget Home Assistant login?")
                .setMessage("Deletes the saved URL and encrypted token from this TV.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Forget", (d, w) -> {
                    assist.disconnect();
                    if (player != null) { player.release(); player = null; }
                    if (speechFocus != null) speechFocus.release();
                    new OverlayPreferences(this).setTvEvents(false);
                    stopService(new Intent(this, TvEventsService.class));
                    store.clear();
                    settings = null;
                    updateConnectionLabel();
                    status("Saved connection deleted.");
                }).show();
    }

    @Override public void haLayout(String setting) {
        if (HaLayout.STYLE.equals(setting)) {
            runOnUiThread(this::applyMainPalette);
        }
    }

    @Override public void status(String message) {
        runOnUiThread(() -> {
            if (status != null) status.setText(message);
            updateConnectionLabel();
        });
    }

    @Override public void listening() {
        runOnUiThread(() -> {
            if (recognized != null) recognized.setText("Listening...");
        });
    }

    @Override public void transcript(String value) {
        runOnUiThread(() -> { if (recognized != null) recognized.setText(value); });
    }

    @Override public void answer(String value) {
        runOnUiThread(() -> { if (response != null) response.setText(value); });
    }

    @Override public void ttsUrl(String url) { runOnUiThread(() -> playTts(url)); }

    private void playTts(String address) {
        if (settings == null) return;
        try {
            HttpUrl playback = TtsUrls.allowedPlaybackUrl(settings.url, address);
            if (player != null) { player.release(); player = null; }
            if (speechFocus == null) speechFocus = new SpeechFocus(this);
            speechFocus.release();
            speechFocus.acquire();
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + settings.token);
            player.setDataSource(this, Uri.parse(playback.toString()), headers);
            player.setOnPreparedListener(p -> { status("Speaking..."); p.start(); });
            player.setOnCompletionListener(p -> {
                if (speechFocus != null) speechFocus.release();
                status("Finished. Select TALK for another question.");
            });
            player.setOnErrorListener((p, what, extra) -> {
                if (speechFocus != null) speechFocus.release();
                status("TTS playback failed. The text answer is still above.");
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            if (speechFocus != null) speechFocus.release();
            status("Could not play TTS. The text answer is still above.");
        }
    }

    @Override protected void onStop() {
        HaDiscovery.release(this);
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopListeningForOverlay();
        if (player != null) { player.release(); player = null; }
        if (speechFocus != null) speechFocus.release();
        if (assist != null) assist.shutdown();
        super.onDestroy();
    }
}
