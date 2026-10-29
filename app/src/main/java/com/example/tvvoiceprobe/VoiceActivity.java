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
 * overlay prototype. Wake word is a separate opt-in and is off until Settings turns it on.
 */
public class VoiceActivity extends Activity implements HaAssistClient.Events {
    private static final int MIC_PERMISSION_REQUEST = 17;
    private static final int OVERLAY_MIC_PERMISSION_REQUEST = 18;
    private static final int OVERLAY_SETTINGS_REQUEST = 19;
    private static final int WAKE_WORD_MIC_REQUEST = 20;
    private static final int FIRST_RUN_PAIRING_REQUEST = 44;
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
    private WakeWordService.Cancellation talkHandoff;
    private int talkGeneration;
    private CredentialStore.Connection settings;
    private MediaPlayer player;
    private SpeechFocus speechFocus;
    private boolean playing;
    private boolean warningPending;
    private UpdateManager updater;
    private boolean manualUpdateCheck;
    private boolean transparentShortcut;
    private boolean initialConnectionRequested;
    /** Only the UI navigation state; no settings or HA data are changed by entering sections. */
    private SettingsSection activeSettingsSection;

    private enum SettingsSection {
        HOME("Home Assistant", "Connection, pairing and conversation", "01"),
        VOICE("Voice & microphone", "Speech pickup, follow-ups and input checks", "02"),
        OVERLAY("Overlay & appearance", "Voice popup, size, position and shortcuts", "03"),
        ALERTS("Alerts & cameras", "TV notifications, speech and live video", "04"),
        SCREEN("Screensaver", "Frigate dashboard, cameras and preview", "05"),
        SYSTEM("Updates & account", "Versions, installation and saved login", "06");

        final String title;
        final String detail;
        final String number;

        SettingsSection(String title, String detail, String number) {
            this.title = title;
            this.detail = detail;
            this.number = number;
        }
    }
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // TV alerts use a different WebSocket from Assist. Refresh the two visible
    // states while this Activity is open, even when no voice event fires.
    private final Runnable connectionLabelRefresh = new Runnable() {
        @Override public void run() {
            updateConnectionLabel();
            mainHandler.postDelayed(this, 2_000L);
        }
    };
    private BroadcastReceiver overlayLaunchReceiver;
    private final Runnable overlayLaunchTimeout = () -> {
        stopListeningForOverlay();
        if (transparentShortcut) openNormalAppFromShortcut();
        else status("Overlay start timed out. Try again.");
    };

    @Override protected void onStart() {
        super.onStart();
        // Recover an interrupted duck from a killed debug process, but never
        // override a user-changed volume while the app was away.
        WakeAudioDucking.recover(this);
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
        // These independent connections can diverge after Home Assistant restarts.
        mainHandler.removeCallbacks(connectionLabelRefresh);
        if (!transparentShortcut && connection != null) {
            mainHandler.post(connectionLabelRefresh);
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

    @Override protected void onResume() {
        super.onResume();
        // Android 14+ permits foreground mic-service creation from a visible
        // Activity, but not from BOOT_COMPLETED. Restore a prior user opt-in
        // automatically when the app becomes visible after a reboot/update.
        if (!transparentShortcut) {
            mainHandler.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    WakeWordStartup.resumeFromVisible(this);
                }
            });
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
                if (UpdateChannel.allowsAutomaticCheck(new OverlayPreferences(VoiceActivity.this).updateChannel())) {
                    rememberUpdateCheck();
                }
                if (manualUpdateCheck) status(message);
                manualUpdateCheck = false;
            }
            @Override public void onAvailable(String version, String notes) {
                boolean development = UpdateChannel.DEVELOPMENT.equals(
                        new OverlayPreferences(VoiceActivity.this).updateChannel());
                if (!development) rememberUpdateCheck();
                manualUpdateCheck = false;
                if (isFinishing() || isDestroyed()) return;
                String shown = notes == null ? "" : notes.substring(0, Math.min(notes.length(), 700));
                new AlertDialog.Builder(VoiceActivity.this)
                        .setTitle(development
                                ? "Development update available: " + version
                                : "Kellie's Voice update available: " + version)
                        .setMessage(development
                                ? "Download this published development prerelease? It is not a stable release.\n\n" + shown
                                : "Download the signed update from GitHub Releases?\n\n" + shown)
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
        addButton(toolbar, "SETTINGS", v -> showSettingsDashboard());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        // A short, focus-safe welcome view on ordinary full-screen launches.
        // Keep the existing root ready underneath, so HA configuration and
        // microphone behavior remain untouched.
        FrameLayout page = new FrameLayout(this);
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));
        KellieSplashView splash = new KellieSplashView(this);
        page.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(page);
        // A missing login gets the visible, no-keyboard local pairing
        // preview. Existing users, assistant key invocations, and the
        // launcher overlay shortcut stay on their original paths.
        boolean savedLoginUnreadable = false;
        try { settings = store.load(); }
        catch (Exception e) {
            savedLoginUnreadable = true;
            status("Saved connection couldn't be decrypted. Configure it again.");
        }
        updateConnectionLabel();
        if (!savedLoginUnreadable && PairingOnboardingPolicy.showOnLaunch(
                settings != null, shouldLaunchOverlayFromLauncher(getIntent()),
                getIntent() != null && Intent.ACTION_ASSIST.equals(getIntent().getAction()))) {
            root.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    startActivityForResult(new Intent(this, PairingSetupActivity.class),
                            FIRST_RUN_PAIRING_REQUEST);
                }
            });
        }
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
            if (UpdateChannel.allowsAutomaticCheck(new OverlayPreferences(this).updateChannel())
                    && now - checks.getLong("last_checked", 0) >= 24L * 60 * 60 * 1000) {
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
        String voice = settings == null ? "Home Assistant voice: not configured"
                : "Home Assistant voice: " + settings.url
                + (assist != null && assist.isConnected() ? "  ✓ connected" : "  (not connected)");
        OverlayPreferences prefs = new OverlayPreferences(this);
        String alert;
        if (!prefs.tvEvents()) alert = "off";
        else if (settings == null) alert = "configure Home Assistant";
        else if (!Settings.canDrawOverlays(this)) alert = "overlay permission needed";
        else {
            String state = TvEventsRecovery.runtime();
            if (TvEventsRecovery.CONNECTED.equals(state)) alert = "✓ connected";
            else if (TvEventsRecovery.RECONNECTING.equals(state)) alert = "reconnecting";
            else if (TvEventsRecovery.DENIED.equals(state)) alert = "permission denied (see TV alert status)";
            else if (TvEventsRecovery.REJECTED.equals(state)) alert = "login rejected";
            else if (TvEventsRecovery.UPDATE_HACS.equals(state)) alert = "check HACS version";
            else alert = "not connected (see TV alert status)";
        }
        connection.setText(voice + "\nTV alerts: " + alert);
    }

    /**
     * Same copper-and-charcoal visual language as the TV overlay.
     * All menus are navigable with the onn D-pad and remain local-only.
     */
    private void showCopperMenu(String heading, String subtitle,
            String[] entries, java.util.function.IntConsumer onSelect) {
        // Child setting screens return to their category instead of dumping
        // the user out to the TV conversation screen.
        showCopperMenu(heading, subtitle, entries, onSelect,
                activeSettingsSection == null ? null : this::showSettingsMenu);
    }

    private void showCopperMenu(String heading, String subtitle,
            String[] entries, java.util.function.IntConsumer onSelect,
            Runnable onBack) {
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
        Button done = copperMenuButton(onBack == null ? "←  Close" : "←  Back");
        done.setOnClickListener(v -> {
            dialog.dismiss();
            if (onBack != null) onBack.run();
        });
        dialog.setOnCancelListener(ignored -> {
            if (onBack != null) onBack.run();
        });
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


    /** The settings landing screen: only six clear destinations, not 21 mixed actions. */
    private void showSettingsDashboard() {
        activeSettingsSection = null;
        AppPalette colors = currentPalette();
        int pad = menuDp(18);
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {colors.menuStart, colors.menuEnd});
        background.setCornerRadius(menuDp(20));
        background.setStroke(menuDp(1), colors.stroke);
        body.setBackground(background);

        TextView kicker = label("KELLIE'S VOICE  /  SETTINGS", 14, colors.accent);
        kicker.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(kicker);
        TextView title = label("Make Kellie yours.", 29, colors.text);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, menuDp(3), 0, menuDp(2));
        body.addView(title);
        TextView description = label("Choose an area below. Press OK to open it, or Back to leave.", 15,
                colors.muted);
        description.setPadding(0, 0, 0, menuDp(13));
        body.addView(description);

        OverlayPreferences prefs = new OverlayPreferences(this);
        String statusText = "VOICE  " + (settings == null ? "Not configured"
                : assist != null && assist.isConnected() ? "Connected" : "Saved")
                + "     •     TV ALERTS  " + (prefs.tvEvents() ? "On" : "Off");
        TextView indicator = label(statusText, 13, colors.text);
        indicator.setPadding(menuDp(14), menuDp(10), menuDp(14), menuDp(10));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(colors.menuButton);
        chip.setCornerRadius(menuDp(10));
        indicator.setBackground(chip);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.bottomMargin = menuDp(12);
        body.addView(indicator, infoParams);

        LinearLayout first = null;
        for (SettingsSection section : SettingsSection.values()) {
            LinearLayout card = settingsCategoryCard(section, colors);
            LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
            row.bottomMargin = menuDp(8);
            body.addView(card, row);
            card.setOnClickListener(v -> {
                dialog.dismiss();
                activeSettingsSection = section;
                showSettingsMenu();
            });
            if (first == null) first = card;
        }

        Button close = copperMenuButton("←  Close settings");
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeRow = new LinearLayout.LayoutParams(-1, -2);
        closeRow.topMargin = menuDp(6);
        body.addView(close, closeRow);
        scroll.addView(body);
        dialog.setView(scroll, 0, 0, 0, 0);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.min(menuDp(880), width - 2 * pad),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        if (first != null) first.requestFocus();
    }

    private int menuDp(int value) {
        return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density));
    }

    /** One large, focus-visible, two-line target per remote action. */
    private LinearLayout settingsCategoryCard(SettingsSection section, AppPalette colors) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(menuDp(18), menuDp(11), menuDp(14), menuDp(11));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setBackground(menuRowBackground(false));

        TextView number = label(section.number, 15, colors.accent);
        number.setTypeface(null, android.graphics.Typeface.BOLD);
        number.setPadding(0, 0, menuDp(19), 0);
        card.addView(number);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(section.title, 20, colors.text);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        copy.addView(name);
        TextView detail = label(section.detail, 14, colors.muted);
        copy.addView(detail);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = label("›", 27, colors.accent);
        arrow.setPadding(menuDp(12), 0, 0, 0);
        card.addView(arrow);
        card.setContentDescription(section.title + ". " + section.detail);
        card.setOnFocusChangeListener((v, focused) -> {
            card.setBackground(menuRowBackground(focused));
            name.setTextColor(focused ? colors.accent : colors.text);
            detail.setTextColor(focused ? colors.text : colors.muted);
        });
        return card;
    }

    /** Called by existing option screens to return to the current section. */
    private void showSettingsMenu() {
        if (activeSettingsSection == null) {
            showSettingsDashboard();
        } else {
            showSettingsSection(activeSettingsSection);
        }
    }

    private void showSettingsSection(SettingsSection section) {
        OverlayPreferences prefs = new OverlayPreferences(this);
        String[] entries;
        switch (section) {
            case HOME:
                entries = new String[] {
                        "Pair with Home Assistant (two-screen code preview)",
                        "Connect / reconnect Home Assistant",
                        "Edit Home Assistant address & token",
                        "Start new conversation",
                        "Manage TV layout from Home Assistant: " + (prefs.haManage() ? "ON" : "OFF")
                };
                break;
            case VOICE:
                entries = new String[] {
                        "Microphone sensitivity: " + OverlayPreferences.noiseLabel(prefs.noise()),
                        "Follow-up listening: " + followUpLabel(),
                        "Microphone input checks: " + (prefs.microphoneDisabled()
                                ? "DISABLED" : prefs.allowUnverifiedMic() ? "Override ON" : "Safe"),
                        "Test microphone hardware",
                        "Wake word: " + (!prefs.wakeWord() ? "OFF"
                                : WakeWordService.isListening() ? "LISTENING" : "STOPPED")
                };
                break;
            case OVERLAY:
                entries = new String[] {
                        "Open floating voice overlay",
                        "Appearance, position and controls"
                };
                break;
            case ALERTS:
                entries = new String[] {
                        "TV notifications & camera popups: " + (prefs.tvEvents() ? "ON" : "OFF"),
                        "Spoken announcements: " + (prefs.spokenAnnouncements() ? "ON" : "OFF"),
                        "Announcement voice volume: " + prefs.spokenAnnouncementVolume() + "%",
                        "Test a spoken announcement",
                        "TV alert status & connection",
                        "Live camera decoder: " + LiveCameraMode.label(prefs.cameraMode())
                };
                break;
            case SCREEN:
                entries = new String[] {
                        "Frigate screensaver: " + (prefs.screensaverDashboard() ? "ON" : "OFF"),
                        "Preview screensaver"
                };
                break;
            case SYSTEM:
                entries = new String[] {
                        "Update channel: " + updateChannelLabel(),
                        "Install downloaded update",
                        "Forget saved Home Assistant connection"
                };
                break;
            default:
                return;
        }
        showCopperMenu("SETTINGS  /  " + section.title.toUpperCase(java.util.Locale.ROOT),
                section.detail + ". Choose an option below. Back returns to all settings.",
                entries, which -> {
                    switch (section) {
                        case HOME:
                            switch (which) {
                                case 0: startActivityForResult(
                                        new Intent(this, PairingSetupActivity.class),
                                        FIRST_RUN_PAIRING_REQUEST); break;
                                case 1: connect(); break;
                                case 2: configure(); break;
                                case 3: newConversation(); break;
                                case 4: chooseHaLayout(); break;
                                default: break;
                            }
                            break;
                        case VOICE:
                            switch (which) {
                                case 0: chooseMicSensitivity(); break;
                                case 1: chooseFollowUp(); break;
                                case 2: chooseMicrophoneDetection(); break;
                                case 3: startActivity(new Intent(this, MainActivity.class)); break;
                                case 4: chooseWakeWord(); break;
                                default: break;
                            }
                            break;
                        case OVERLAY:
                            switch (which) {
                                case 0: launchOverlay(); break;
                                case 1: showOverlayPreferences(); break;
                                default: break;
                            }
                            break;
                        case ALERTS:
                            switch (which) {
                                case 0: chooseTvNotifications(); break;
                                case 1: toggleSpokenAnnouncements(); break;
                                case 2: chooseAnnouncementVolume(); break;
                                case 3: testSpokenAnnouncements(); break;
                                case 4: showTvAlertStatus(); break;
                                case 5: chooseCameraMode(); break;
                                default: break;
                            }
                            break;
                        case SCREEN:
                            switch (which) {
                                case 0: showScreensaverSettings(); break;
                                case 1: startActivity(new Intent(this, ScreensaverPreviewActivity.class)); break;
                                default: break;
                            }
                            break;
                        case SYSTEM:
                            switch (which) {
                                case 0: chooseUpdates(); break;
                                case 1: promptInstallUpdate(); break;
                                case 2: confirmForget(); break;
                                default: break;
                            }
                            break;
                        default: break;
                    }
                }, this::showSettingsDashboard);
    }

    private void chooseAnnouncementVolume() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        int[] levels = SpeechVolume.levels();
        String[] choices = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            choices[i] = (prefs.spokenAnnouncementVolume() == levels[i] ? "✓  " : "     ")
                    + levels[i] + "%";
        }
        showCopperMenu("TV ALERTS  ✦  VOICE VOLUME",
                "Adjust only spoken TV announcements. This does not change " +
                        "YouTube, media volume, timers, or assistant replies. " +
                        "Choose a level, then use Test spoken announcements locally to hear it.",
                choices, which -> {
                    if (which < 0 || which >= levels.length) return;
                    prefs.setSpokenAnnouncementVolume(levels[which]);
                    status("Announcement voice volume: " + prefs.spokenAnnouncementVolume() + "%");
                    showSettingsMenu();
                });
    }

    private void testSpokenAnnouncements() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        if (!prefs.tvEvents() || !prefs.spokenAnnouncements()) {
            status("Enable TV alerts and Spoken announcements before this local test.");
            showSettingsMenu();
            return;
        }
        try {
            startForegroundService(new Intent(this, TvEventsService.class)
                    .setAction(TvEventsService.ACTION_TEST_SPOKEN));
            status("Local speech test requested. Check TV sound and TV alert status.");
        } catch (RuntimeException ignored) {
            status("Android could not start the TV alert speech self-test.");
        }
        showSettingsMenu();
    }

    private void toggleSpokenAnnouncements() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        prefs.setSpokenAnnouncements(!prefs.spokenAnnouncements());
        status(prefs.spokenAnnouncements()
                ? "Spoken announcements enabled for alerts with a speak sentence."
                : "Spoken announcements disabled. Text popups remain active.");
        showSettingsMenu();
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
                        if (prefs.haManage() || prefs.screensaverDashboard()) {
                            ScreensaverWatchService.startIfEnabled(this);
                        } else {
                            stopService(new Intent(this, ScreensaverWatchService.class));
                        }
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
        String diagnostic = TvEventsRecovery.diagnostics();
        if (!diagnostic.isEmpty()) message += "\n\nLast receiver activity: " + diagnostic;
        message += "\n\nLast spoken alert: " + TvSpeechStatus.latest();
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
        boolean disabled = prefs.microphoneDisabled();
        showCopperMenu("SETTINGS  ✦  MICROPHONE ROUTING",
                "Some TVs advertise a phantom built-in microphone or an echo "
                        + "reference that cannot capture speech. If your TV has "
                        + "no accessible microphone, disable capture here. "
                        + "The existing compatibility override cannot bypass this block.",
                new String[] {
                        disabled ? "✓  Microphone DISABLED on this TV (re-enable)"
                                : "Disable microphone capture on this TV",
                        bypass ? "Turn compatibility override OFF (recommended)"
                                : "Try unverified microphone (compatibility override)",
                        "Open microphone diagnostics",
                        "Back to Settings"
                }, which -> {
                    if (which == 0) {
                        boolean nowDisabled = !disabled;
                        prefs.setMicrophoneDisabled(nowDisabled);
                        if (nowDisabled) {
                            prefs.setWakeWord(false);
                            WakeWordStartup.clearReminder(this);
                            stopService(new Intent(this, WakeWordService.class));
                        }
                        status(disabled
                                ? "Microphone capture re-enabled. Normal route checks apply."
                                : "Microphone capture disabled on this TV. No Assist audio will start.");
                        showSettingsMenu();
                    } else if (which == 1) {
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
                                            + "turn this off after testing. A manual microphone "
                                            + "disable always takes priority.")
                                    .setNegativeButton("Cancel", (dialog, w) -> showSettingsMenu())
                                    .setPositiveButton("Try anyway", (dialog, w) -> {
                                        prefs.setAllowUnverifiedMic(true);
                                        status("Microphone route check overridden for diagnostics.");
                                        showSettingsMenu();
                                    }).show();
                        }
                    } else if (which == 2) {
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

    private String updateChannelLabel() {
        return UpdateChannel.DEVELOPMENT.equals(new OverlayPreferences(this).updateChannel())
                ? "Development" : "Stable";
    }

    private void chooseUpdates() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean development = UpdateChannel.DEVELOPMENT.equals(prefs.updateChannel());
        showCopperMenu("SETTINGS  ✦  APP UPDATES",
                "Stable is the normal public release. Development looks only for a published prerelease and never checks by itself. "
                        + "Neither choice uses a token. A signed Actions artifact is not an in-app update. "
                        + "Android cannot install an older stable build over a newer development build.",
                new String[] {
                        development ? "✓  Development prereleases" : "     Development prereleases",
                        development ? "     Stable public releases" : "✓  Stable public releases",
                        "Check now",
                        "Back to Settings"
                }, which -> {
                    if (which == 0 && !development) {
                        prefs.setUpdateChannel(UpdateChannel.DEVELOPMENT);
                        updater.clearOffer();
                        status("Development updates on. Choose Check now. Nothing downloads until you say so.");
                    } else if (which == 1 && development) {
                        prefs.setUpdateChannel(UpdateChannel.STABLE);
                        updater.clearOffer();
                        status("Stable updates on. A newer stable release can replace this build. An older one cannot.");
                    } else if (which == 2) {
                        manualUpdateCheck = true;
                        status(development
                                ? "Checking published development prereleases..."
                                : "Checking GitHub Releases...");
                        updater.check();
                    }
                    showSettingsMenu();
                });
    }

    private String followUpLabel() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        if (!prefs.followUp()) return "OFF";
        return "ON, " + prefs.followUpSeconds() + " seconds";
    }

    private void chooseFollowUp() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        showCopperMenu("SETTINGS  ✦  FOLLOW-UP",
                "After Julia finishes speaking, listen for another question. This stays off until you turn it on here.",
                new String[] {
                        "Off",
                        "On, stop after 5 seconds of silence",
                        "On, stop after 8 seconds of silence",
                        "On, stop after 10 seconds of silence",
                        "On, stop after 15 seconds of silence"
                }, which -> {
                    if (which == 0) prefs.setFollowUp(false);
                    else {
                        prefs.setFollowUp(true);
                        prefs.setFollowUpSeconds(which == 1 ? 5 : which == 2 ? 8 : which == 3 ? 10 : 15);
                    }
                    pushLayout(HaLayout.FOLLOW_TIMEOUT);
                    status("Follow-up listening: " + followUpLabel());
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
        if (requestCode == FIRST_RUN_PAIRING_REQUEST && resultCode == RESULT_OK) {
            // The owner explicitly chose the existing manual login fallback.
            // Do not modify existing credentials or start an Assist session.
            configure();
            return;
        }
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
        if (assist == null || !assist.isConnected()) {
            if (assist != null && assist.canReconnect()) {
                assist.ensureConnected();
                // Full-screen TALK is not queued. Explicit consent must be renewed
                // after authentication, unlike the short-lived overlay request.
                status(AssistReadinessPolicy.waitingForConnection());
            } else {
                status("Home Assistant is not connected. Check login in SETTINGS.");
            }
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
            final int generation = ++talkGeneration;
            if (talkHandoff != null) talkHandoff.cancel();
            status("Waiting for the microphone...");
            talkHandoff = WakeWordService.requestHandoff(MicLease.TALK, new WakeWordService.Handoff() {
                @Override public void accepted(WakeWordService.Cancellation cancellation) {
                    runOnUiThread(() -> {
                        if (generation != talkGeneration || isDestroyed()) {
                            MicLease.release(MicLease.TALK);
                            return;
                        }
                        cancellation.markStarted();
                        if (!assist.talk()) MicLease.release(MicLease.TALK);
                    });
                }

                @Override public void denied() {
                    runOnUiThread(() -> {
                        if (generation == talkGeneration) {
                            status("The microphone is in use. Close the overlay or leave diagnostics first.");
                        }
                    });
                }
            });
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
        } else if (requestCode == WAKE_WORD_MIC_REQUEST) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) enableWakeWord();
            else status("Microphone permission denied. Wake word stays off.");
        }
    }

    private void newConversation() {
        if (player != null) { player.release(); player = null; }
        if (speechFocus != null) speechFocus.release();
        assist.clearConversation();
        if (!assist.pipelineIdle()) assist.finishTalking();
        recognized.setText("Nothing yet");
        response.setText("Ready for a fresh conversation.");
        status(settings == null
                ? "Conversation cleared. Configure Home Assistant to continue."
                : "Started a new conversation.");
    }

    private void chooseWakeWord() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean enabled = prefs.wakeWord();
        boolean active = WakeWordService.isListening();
        showCopperMenu("SETTINGS  ✦  WAKE WORD",
                "Listens on this TV for Okay Nabu, then opens the voice overlay. "
                        + "Off by default. Your ON choice is saved across reboots and updates. "
                        + "Android TV may require you to open the app once after reboot to resume. "
                        + "While it is LISTENING, the microphone stays in use and Android's privacy indicator stays on. "
                        + "Hey Google is separate and can still take the microphone. "
                        + "Nothing is sent until the phrase is recognized. The recorder then keeps that question until Assist takes it, for at most eight seconds. "
                        + "That clip, plus whatever is still being said, goes to your Assist pipeline. "
                        + "A short beep means the microphone is ready. "
                        + "Audio ducking starts only AFTER Okay Nabu is detected, not before.",
                new String[] {
                        enabled && active ? "Turn wake word OFF"
                                : enabled ? "Resume wake word listener" : "Turn wake word ON",
                        "Wake audio ducking: " + (prefs.wakeDuckingPercent() == 0
                                ? "OFF" : prefs.wakeDuckingPercent() + "% quieter"),
                        "Back to Settings"
                }, which -> {
                    if (which == 0) {
                        if (enabled && active) {
                            prefs.setWakeWord(false);
                            WakeWordStartup.clearReminder(this);
                            stopService(new Intent(this, WakeWordService.class));
                            status("Wake word off. The microphone is not listening.");
                        } else {
                            enableWakeWord();
                        }
                    } else if (which == 1) {
                        chooseWakeAudioDucking();
                        return;
                    }
                    showSettingsMenu();
                });
    }

    private void chooseWakeAudioDucking() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        int[] levels = WakeDuckingLevels.levels();
        String[] choices = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            choices[i] = (prefs.wakeDuckingPercent() == levels[i] ? "✓  " : "     ")
                    + (levels[i] == 0 ? "OFF" : levels[i] + "% quieter");
        }
        showCopperMenu("WAKE WORD  ✦  AUDIO DUCKING",
                "After Okay Nabu, reduce the TV's local media volume while Kellie "
                        + "listens and answers. Original volume is restored when the "
                        + "overlay closes. This cannot improve hearing the wake phrase "
                        + "itself. Some HDMI/CEC and fixed-volume TVs cannot be controlled.",
                choices, which -> {
                    if (which < 0 || which >= levels.length) return;
                    prefs.setWakeDuckingPercent(levels[which]);
                    status("Wake audio ducking: " + (levels[which] == 0
                            ? "OFF" : levels[which] + "% quieter"));
                    chooseWakeWord();
                });
    }

    private void enableWakeWord() {
        if (settings == null) {
            status("Configure Home Assistant before enabling the wake word.");
            return;
        }
        OverlayPreferences micPrefs = new OverlayPreferences(this);
        if (micPrefs.microphoneDisabled()) {
            status(MicRoutePolicy.MANUALLY_DISABLED_MESSAGE);
            return;
        }
        String routeIssue = MicrophoneRouting.preflight(this, micPrefs.allowUnverifiedMic());
        if (routeIssue != null) {
            status(routeIssue);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            status("Grant 'Display over other apps' before enabling the wake word.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, WAKE_WORD_MIC_REQUEST);
            return;
        }
        OverlayPreferences prefs = new OverlayPreferences(this);
        prefs.setWakeWord(true);
        try {
            startForegroundService(new Intent(this, WakeWordService.class)
                    .setAction(WakeWordService.ACTION_START));
            status("Wake word ON and saved. It resumes when this app opens after reboot.");
        } catch (RuntimeException ignored) {
            prefs.setWakeWord(false);
            status("Android couldn't start the wake word listener.");
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
                    OverlayPreferences prefs = new OverlayPreferences(this);
                    // Forget must immediately disarm the always-listening mic.
                    prefs.setWakeWord(false);
                    WakeWordStartup.clearReminder(this);
                    stopService(new Intent(this, WakeWordService.class));
                    prefs.setTvEvents(false);
                    stopService(new Intent(this, TvEventsService.class));
                    store.clear();
                    settings = null;
                    updateConnectionLabel();
                    status("Saved connection deleted.");
                }).show();
    }

    @Override public void captureReleased(int runId) {
        MicLease.release(MicLease.TALK);
    }

    @Override public void runEnded() {
        // Home Assistant can emit run-end BEFORE the AudioRecord worker has
        // finished release(). Keep TALK reserved until captureReleased().
        // HaAssistClient emits captureReleased even if no recorder was started.
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
        mainHandler.removeCallbacks(connectionLabelRefresh);
        HaDiscovery.release(this);
        super.onStop();
    }

    @Override protected void onDestroy() {
        talkGeneration++;
        if (talkHandoff != null) talkHandoff.cancel();
        stopListeningForOverlay();
        if (player != null) { player.release(); player = null; }
        if (speechFocus != null) speechFocus.release();
        if (assist != null) assist.shutdown();
        super.onDestroy();
    }

    /** Configure the opt-in dashboard entirely on the TV. No HA or Frigate settings change. */
    private void showScreensaverSettings() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        String[] cameras = ScreensaverDashboard.cameraList(prefs.screensaverCameras());
        showCopperMenu("SETTINGS  ✦  FRIGATE SCREENSAVER",
                "Choose one view: a Frigate web page, or up to four Home Assistant camera " +
                "snapshots. A saved Frigate URL takes priority; clear it to use the camera grid. " +
                "No microphone is started.",
                new String[] {
                        prefs.screensaverDashboard() ? "Turn Frigate screensaver OFF" : "Turn Frigate screensaver ON",
                        "Frigate web URL: " + (prefs.screensaverUrl().isEmpty() ? "Not set" : "Configured (edit / clear)"),
                        "Home Assistant cameras: " + cameras.length + " configured (edit / clear)",
                        "Dashboard size: " + ScreensaverDashboard.sizeLabel(prefs.screensaverSize()),
                        "Frigate page layout: " + (prefs.screensaverDesktopPage() ? "Desktop TV" : "Automatic"),
                        "Frigate page fit: " + prefs.screensaverFit(),
                        "HA camera grid playback: " + prefs.screensaverLiveMode(),
                        "Choose Android screensaver",
                        "Preview Frigate dashboard",
                        "Screensaver status & permissions",
                        "Test Frigate as TV overlay",
                        "Back to Settings"
                }, which -> {
                    switch (which) {
                        case 0: toggleScreensaver(); break;
                        case 1: editScreensaverUrl(); break;
                        case 2: editScreensaverCameras(); break;
                        case 3: chooseScreensaverSize(); break;
                        case 4: toggleScreensaverDesktopPage(); break;
                        case 5: chooseScreensaverFit(); break;
                        case 6: chooseScreensaverLiveMode(); break;
                        case 7: openAndroidScreensaverList(); break;
                        case 8: startActivity(new Intent(this, ScreensaverPreviewActivity.class)); break;
                        case 9: showScreensaverDiagnostics(); break;
                        case 10: testScreensaverOverlay(); break;
                        case 11: showSettingsMenu(); break;
                        default: break;
                    }
                });
    }

    private EditText screensaverInput(String value, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setHint(hint);
        input.setText(value);
        input.setSelection(input.getText().length());
        return input;
    }

    private LinearLayout screensaverInputView(EditText input) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, pad / 2);
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return form;
    }

    private void editScreensaverUrl() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        EditText input = screensaverInput(prefs.screensaverUrl(),
                "http://frigate.local:5000/",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Frigate web page")
                .setMessage("Enter your Frigate address on the local network. " +
                        "Leave empty to use Home Assistant camera snapshots instead. " +
                        "Never put passwords or tokens in this URL.")
                .setView(screensaverInputView(input))
                .setNegativeButton("Cancel", (d, w) -> showScreensaverSettings())
                .setPositiveButton("Save", (d, w) -> {
                    String typed = input.getText().toString().trim();
                    String safe = ScreensaverDashboard.dashboardUrl(typed);
                    if (!typed.isEmpty() && safe.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setTitle("Invalid Frigate URL")
                                .setMessage("Use a full http:// or https:// address without a password or token.")
                                .setPositiveButton("Edit again", (error, choice) -> editScreensaverUrl())
                                .show();
                        return;
                    }
                    prefs.setScreensaverUrl(safe);
                    pushLayout(HaLayout.SCREENSAVER_URL);
                    ScreensaverSignals.settingsChanged();
                    showScreensaverSettings();
                })
                .show();
    }

    private void editScreensaverCameras() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        EditText input = screensaverInput(prefs.screensaverCameras(),
                "camera.driveway, camera.doorbell",
                InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Home Assistant camera entities")
                .setMessage("Enter up to four real Home Assistant camera.* entity IDs, separated " +
                        "by commas. Leave empty to clear the grid. These are snapshots, " +
                        "not Frigate camera labels or RTSP URLs.")
                .setView(screensaverInputView(input))
                .setNegativeButton("Cancel", (d, w) -> showScreensaverSettings())
                .setPositiveButton("Save", (d, w) -> {
                    String typed = input.getText().toString().trim();
                    String[] ids = typed.isEmpty() ? new String[0] : typed.split("[,\\s]+");
                    boolean invalid = ids.length > ScreensaverDashboard.MAX_CAMERAS;
                    for (String id : ids) {
                        if (TvEventPayload.cameraEntity(id) == null) invalid = true;
                    }
                    if (invalid) {
                        new AlertDialog.Builder(this)
                                .setTitle("Check camera entity IDs")
                                .setMessage("Enter one to four valid camera.* entity IDs. " +
                                        "Find the exact IDs in Home Assistant -> Developer Tools -> States.")
                                .setPositiveButton("Edit again", (error, choice) -> editScreensaverCameras())
                                .show();
                        return;
                    }
                    prefs.setScreensaverCameras(typed);
                    pushLayout(HaLayout.SCREENSAVER_CAMERAS);
                    ScreensaverSignals.settingsChanged();
                    showScreensaverSettings();
                })
                .show();
    }

    private void chooseScreensaverSize() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        final String[] sizes = {"small", "medium", "large"};
        String[] labels = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            labels[i] = (sizes[i].equals(prefs.screensaverSize()) ? "✓  " : "     ") +
                    ScreensaverDashboard.sizeLabel(sizes[i]);
        }
        showCopperMenu("FRIGATE  ✦  DASHBOARD SIZE",
                "Small leaves more TV content visible; Large fills the screen.",
                labels, which -> {
                    prefs.setScreensaverSize(sizes[which]);
                    pushLayout(HaLayout.SCREENSAVER_SIZE);
                    ScreensaverSignals.settingsChanged();
                    showScreensaverSettings();
                });
    }

    private void toggleScreensaverDesktopPage() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        prefs.setScreensaverDesktopPage(!prefs.screensaverDesktopPage());
        pushLayout(HaLayout.SCREENSAVER_PAGE_LAYOUT);
        ScreensaverSignals.settingsChanged();
        showScreensaverSettings();
    }

    private void chooseScreensaverFit() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        String[] options = {"Fit more cameras", "Balanced", "Larger text"};
        showCopperMenu("FRIGATE  ✦  PAGE FIT",
                "Fit more cameras shows more of Frigate's live grid without scrolling. "
                        + "Balanced matches the previous view, while Larger text is easier to read.",
                options, which -> {
                    prefs.setScreensaverFit(options[which]);
                    pushLayout(HaLayout.SCREENSAVER_FIT);
                    ScreensaverSignals.settingsChanged();
                    showScreensaverSettings();
                });
    }

    private void chooseScreensaverLiveMode() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        String[] options = {ScreensaverLiveMode.SNAPSHOTS, ScreensaverLiveMode.ONE,
                ScreensaverLiveMode.TWO, ScreensaverLiveMode.FOUR};
        showCopperMenu("FRIGATE  ✦  CAMERA GRID",
                "Without a Frigate URL, request muted live streams for selected HA cameras. "
                        + "Test one live camera first. Other tiles remain snapshots. "
                        + "Four live streams are advanced and may overload some TVs. "
                        + "Any failed stream falls back to snapshots.",
                options, which -> {
                    prefs.setScreensaverLiveMode(options[which]);
                    pushLayout(HaLayout.SCREENSAVER_LIVE);
                    ScreensaverSignals.settingsChanged();
                    showScreensaverSettings();
                });
    }

    private void openAndroidScreensaverList() {
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } catch (Exception ignored) {
            new AlertDialog.Builder(this)
                    .setTitle("Android screensaver settings unavailable")
                    .setMessage("Open the TV's Android Settings -> Display -> Screensaver and " +
                            "choose Kellie's Voice Frigate if the firmware supports it.")
                    .setPositiveButton("Back", (d, w) -> showScreensaverSettings())
                    .show();
        }
    }

    private void showScreensaverDiagnostics() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean overlay = Settings.canDrawOverlays(this);
        boolean configured = ScreensaverDashboard.configured(
                prefs.screensaverUrl(), prefs.screensaverCameras());
        int localCameras = ScreensaverDashboard.cameraList(prefs.screensaverCameras()).length;
        String source = !prefs.screensaverUrl().isEmpty() ? "Frigate web page"
                : localCameras > 0 ? "HA snapshot grid" : "NONE - no source on this TV";
        String info = "Frigate feature: " + (prefs.screensaverDashboard() ? "ON" : "OFF")
                + "\nWatcher: " + (ScreensaverWatchStatus.running() ? "RUNNING" : "NOT RUNNING")
                + "\nOverlay permission: " + (overlay ? "ALLOWED" : "NOT ALLOWED")
                + "\nHome Assistant layout: " + (prefs.haManage() ? "ON" : "OFF")
                + "\nSource on TV: " + source
                + "\nCameras saved on TV: " + localCameras
                + "\nConfigured: " + (configured ? "YES" : "NO")
                + "\nHA settings link: " + ScreensaverWatchStatus.settingsSync()
                + "\nLast HA source: " + ScreensaverWatchStatus.sourceSync()
                + "\nLast Android dream event: " + ScreensaverWatchStatus.lastEvent()
                + "\nLast overlay outcome: " + ScreensaverWatchStatus.lastOverlay()
                + "\n\nIf HA shows four cameras but the TV reports zero, "
                + "enable Home Assistant layout on this TV, then reopen diagnostics. "
                + "The snapshot grid requires the saved HA connection; "
                + "the Frigate website uses its own URL. No microphone is activated.";
        new AlertDialog.Builder(this)
                .setTitle("Frigate screensaver diagnostics")
                .setMessage(info)
                .setNegativeButton("Back", (d, w) -> showScreensaverSettings())
                .setPositiveButton(prefs.haManage() ? "Resync HA cameras" : "Resume watcher", (d, w) -> {
                    if (prefs.haManage()) ScreensaverWatchService.refreshFromHa(this);
                    else ScreensaverWatchService.startIfEnabled(this);
                    showScreensaverSettings();
                })
                .show();
    }

    private void testScreensaverOverlay() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        if (!prefs.screensaverDashboard()) {
            new AlertDialog.Builder(this).setTitle("Screensaver is off")
                    .setMessage("Turn Frigate screensaver ON before trying its overlay.")
                    .setPositiveButton("Back", (d, w) -> showScreensaverSettings())
                    .show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("Allow the Frigate overlay")
                    .setMessage("Android needs Display over other apps permission. "
                            + "Your existing notification permission may already cover it.")
                    .setNegativeButton("Back", (d, w) -> showScreensaverSettings())
                    .setPositiveButton("Open permission", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (RuntimeException ignored) {
                            status("TV could not open its display-over-apps permission screen.");
                        }
                    }).show();
            return;
        }
        if (!ScreensaverDashboard.configured(prefs.screensaverUrl(), prefs.screensaverCameras())) {
            new AlertDialog.Builder(this).setTitle("Choose Frigate cameras first")
                    .setMessage("Set a Frigate URL or Home Assistant camera entities.")
                    .setPositiveButton("Back", (d, w) -> showScreensaverSettings()).show();
            return;
        }
        try {
            startForegroundService(new Intent(this, ScreensaverWatchService.class)
                    .setAction(ScreensaverWatchService.ACTION_TEST_OVERLAY));
        } catch (RuntimeException ignored) {
            status("Android blocked the Frigate overlay test. Check screensaver diagnostics.");
            showScreensaverSettings();
        }
    }

    private void toggleScreensaver() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean next = !prefs.screensaverDashboard();
        prefs.setScreensaverDashboard(next);
        pushLayout(HaLayout.SCREENSAVER_ENABLED);
        if (next || prefs.haManage()) ScreensaverWatchService.startIfEnabled(this);
        else stopService(new Intent(this, ScreensaverWatchService.class)
                .setAction(ScreensaverWatchService.ACTION_STOP));
        ScreensaverSignals.settingsChanged();
        showScreensaverSettings();
    }
}
