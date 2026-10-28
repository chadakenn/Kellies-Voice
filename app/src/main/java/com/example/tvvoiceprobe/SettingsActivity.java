package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Persistent full-screen Settings host for Android TV.
 * Matches the copper/charcoal two-column reference: icons, switches,
 * focus borders, and panels. All preference changes happen here so
 * VoiceActivity's singleTask launch mode cannot destroy this Activity.
 */
public class SettingsActivity extends Activity {
    private static final int WAKE_MIC_REQUEST = 21;

    private AppPalette colors;
    private LinearLayout contentArea;
    private TextView statusCredentials;
    private TextView statusWake;
    private TextView statusAlerts;
    private String selectedCategory = "Voice & microphone";
    private View focusedCategoryCard;

    private static final String[][] CATEGORIES = {
        {"Home Assistant", "Connection, pairing and conversation", "\uD83C\uDFE0"},
        {"Voice & microphone", "Wake word, sensitivity and input", "\uD83C\uDFA4"},
        {"Overlay & appearance", "Popup, size, position and theme", "\uD83C\uDFA8"},
        {"Alerts & cameras", "Notifications, speech and video", "\uD83D\uDD14"},
        {"Screensaver", "Frigate dashboard and cameras", "\uD83D\uDCBB"},
        {"Updates & account", "Versions and saved login", "\uD83D\uDC64"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        colors = AppPalette.forSkin(new OverlayPreferences(this).skin());
        if (savedInstanceState != null) {
            selectedCategory = savedInstanceState.getString("category", selectedCategory);
        }
        buildUi();
        refreshStatus();
        showCategory(selectedCategory);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("category", selectedCategory);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        showCategory(selectedCategory);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(22), dp(32), dp(18));
        root.setBackgroundColor(colors.screen);

        // Header + status strip
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = makeText("Kellie's Voice", 22, colors.accent, true);
        brand.setPadding(0, 0, dp(24), 0);
        header.addView(brand);

        statusCredentials = statusChip("Credentials");
        statusWake = statusChip("Wake word");
        statusAlerts = statusChip("TV alerts");
        header.addView(statusCredentials);
        header.addView(spacer(10));
        header.addView(statusWake);
        header.addView(spacer(10));
        header.addView(statusAlerts);
        root.addView(header);

        TextView subtitle = makeText("Settings", 28, colors.text, true);
        subtitle.setPadding(0, dp(10), 0, dp(14));
        root.addView(subtitle);

        // Two columns
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);

        ScrollView leftScroll = new ScrollView(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(0, 0, dp(16), 0);
        View firstCard = null;
        for (String[] cat : CATEGORIES) {
            View card = categoryCard(cat[0], cat[1], cat[2]);
            left.addView(card);
            if (firstCard == null) firstCard = card;
            if (cat[0].equals(selectedCategory)) focusedCategoryCard = card;
        }
        leftScroll.addView(left);
        columns.addView(leftScroll, new LinearLayout.LayoutParams(0, -1, 1.05f));

        // Right panel
        LinearLayout rightPanel = new LinearLayout(this);
        rightPanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(colors.menuButton);
        panelBg.setCornerRadius(dp(16));
        panelBg.setStroke(dp(1), colors.stroke);
        rightPanel.setBackground(panelBg);
        rightPanel.setPadding(dp(18), dp(16), dp(18), dp(16));

        ScrollView rightScroll = new ScrollView(this);
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        rightScroll.addView(contentArea);
        rightPanel.addView(rightScroll, new LinearLayout.LayoutParams(-1, -1));
        columns.addView(rightPanel, new LinearLayout.LayoutParams(0, -1, 1.6f));

        root.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = makeText("D-pad to move  •  OK to select  •  Back to return", 14, colors.muted, false);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint);

        setContentView(root);
        if (focusedCategoryCard != null) focusedCategoryCard.requestFocus();
        else if (firstCard != null) firstCard.requestFocus();
    }

    private View categoryCard(String title, String detail, String icon) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(title);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.menuButton);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(2), colors.stroke);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView iconView = makeText(icon, 22, colors.accent, false);
        iconView.setPadding(0, 0, dp(12), 0);
        card.addView(iconView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.addView(makeText(title, 17, colors.text, true));
        TextView sub = makeText(detail, 13, colors.muted, false);
        sub.setPadding(0, dp(2), 0, 0);
        textCol.addView(sub);
        card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        card.setOnFocusChangeListener((v, hasFocus) -> applyCategoryFocus(v, hasFocus));
        card.setOnClickListener(v -> {
            selectedCategory = title;
            applyCategoryFocus(v, true);
            showCategory(title);
        });
        return card;
    }

    private void applyCategoryFocus(View card, boolean focused) {
        GradientDrawable shape = (GradientDrawable) card.getBackground();
        boolean selected = selectedCategory.equals(card.getTag());
        shape.setStroke(focused || selected ? dp(3) : dp(1), focused || selected ? colors.focus : colors.stroke);
        shape.setColor(focused || selected ? colors.menuFocus : colors.menuButton);
        if (focused) {
            selectedCategory = (String) card.getTag();
            showCategory(selectedCategory);
        }
    }

    private void showCategory(String category) {
        contentArea.removeAllViews();
        OverlayPreferences prefs = new OverlayPreferences(this);
        TextView heading = makeText(category, 22, colors.accent, true);
        heading.setPadding(0, 0, 0, dp(6));
        contentArea.addView(heading);
        contentArea.addView(divider());

        switch (category) {
            case "Home Assistant":
                addActionRow("Connect / reconnect", "Use saved credentials", "\uD83D\uDD0C", () -> doConnect());
                addActionRow("Edit address & token", "Change Home Assistant login", "\u270F", () -> doConfigure());
                addActionRow("Start new conversation", "Clear current Assist context", "\uD83D\uDCAC", () -> doNewConversation());
                addSwitchRow("Manage layout from HA", "Allow Home Assistant to set theme", prefs.haManage(),
                        (btn, on) -> prefs.setHaManage(on));
                break;
            case "Voice & microphone":
                addSwitchRow("Wake word", "Okay Nabu listener (opt-in)", prefs.wakeWord() && WakeWordService.isListening(),
                        (btn, on) -> toggleWakeWord(on));
                addActionRow("Microphone sensitivity", OverlayPreferences.noiseLabel(prefs.noise()), "\uD83D\uDD0A",
                        () -> chooseSensitivity(prefs));
                addSwitchRow("Follow-up listening", "Hands-free replies", prefs.followUp(),
                        (btn, on) -> prefs.setFollowUp(on));
                addActionRow("Microphone input checks", prefs.microphoneDisabled() ? "Disabled" : "Safe", "\uD83C\uDFA7",
                        () -> chooseMicChecks(prefs));
                addActionRow("Test microphone hardware", "Open diagnostics", "\uD83D\uDD0D",
                        () -> startActivity(new Intent(this, MainActivity.class)));
                break;
            case "Overlay & appearance":
                addActionRow("Open floating overlay", "Launch voice popup", "\uD83D\uDCAC", () -> doLaunchOverlay());
                addActionRow("Appearance & position", "Size, position, skin", "\uD83C\uDFA8", () -> doOverlayPrefs());
                break;
            case "Alerts & cameras":
                addSwitchRow("TV notifications", "Announcements and camera popups", prefs.tvEvents(),
                        (btn, on) -> toggleTvEvents(on));
                addSwitchRow("Spoken announcements", "Speak alert text", prefs.spokenAnnouncements(),
                        (btn, on) -> prefs.setSpokenAnnouncements(on));
                addActionRow("Announcement volume", prefs.spokenAnnouncementVolume() + "%", "\uD83D\uDD0A",
                        () -> chooseVolume(prefs));
                addActionRow("TV alert status", "Connection and recovery", "\u2139", () -> showAlertStatus());
                addActionRow("Live camera decoder", LiveCameraMode.label(prefs.cameraMode()), "\uD83D\uDCF7",
                        () -> chooseCameraMode(prefs));
                break;
            case "Screensaver":
                addSwitchRow("Frigate screensaver", "Show dashboard while dreaming", prefs.screensaverDashboard(),
                        (btn, on) -> prefs.setScreensaverDashboard(on));
                addActionRow("Screensaver settings", "URL, cameras, size, fit", "\u2699", () -> showScreensaverInfo());
                addActionRow("Preview screensaver", "Open preview", "\u25B6", () ->
                        startActivity(new Intent(this, ScreensaverPreviewActivity.class)));
                break;
            case "Updates & account":
                addActionRow("Check for updates", "Look for a signed release", "\u2B06", () -> doCheckUpdates());
                addActionRow("Install downloaded update", "Use verified APK", "\u2B07", () -> doInstallUpdate());
                addActionRow("Forget saved connection", "Remove URL and token", "\u26A0", () -> doForget());
                break;
            default:
                break;
        }
    }

    private void addActionRow(String title, String subtitle, String icon, Runnable onClick) {
        LinearLayout row = optionRowBase();
        row.addView(iconText(icon));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(makeText(title, 16, colors.text, true));
        TextView sub = makeText(subtitle, 13, colors.muted, false);
        sub.setPadding(0, dp(2), 0, 0);
        text.addView(sub);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> onClick.run());
        contentArea.addView(row);
        contentArea.addView(divider());
    }

    private void addSwitchRow(String title, String subtitle, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = optionRowBase();
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(makeText(title, 16, colors.text, true));
        TextView sub = makeText(subtitle, 13, colors.muted, false);
        sub.setPadding(0, dp(2), 0, 0);
        text.addView(sub);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setFocusable(false);
        sw.setClickable(false);
        row.addView(sw);
        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next);
            listener.onCheckedChanged(sw, next);
            refreshStatus();
        });
        contentArea.addView(row);
        contentArea.addView(divider());
    }

    private LinearLayout optionRowBase() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(12), dp(10), dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00000000);
        bg.setCornerRadius(dp(8));
        row.setBackground(bg);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable shape = (GradientDrawable) v.getBackground();
            shape.setStroke(hasFocus ? dp(3) : 0, colors.focus);
            shape.setColor(hasFocus ? colors.menuFocus : 0x00000000);
        });
        return row;
    }

    private TextView iconText(String icon) {
        TextView t = makeText(icon, 18, colors.accent, false);
        t.setPadding(0, 0, dp(12), 0);
        return t;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(colors.stroke);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        lp.topMargin = dp(2);
        lp.bottomMargin = dp(2);
        d.setLayoutParams(lp);
        return d;
    }

    // --- In-place actions (Settings stays alive) ---

    private void doConnect() {
        try {
            CredentialStore.Connection c = new CredentialStore(this).load();
            if (c == null) {
                toast("Configure Home Assistant first.");
                return;
            }
            // Connection is owned by VoiceActivity's Assist client. Signal it without
            // bringing the singleTask root to the front.
            getSharedPreferences("tv_voice_settings_signals", MODE_PRIVATE)
                    .edit().putBoolean("request_connect", true).apply();
            toast("Reconnect requested. Return to the voice screen to complete.");
        } catch (Exception e) {
            toast("Could not read saved credentials.");
        }
    }

    private void doConfigure() {
        CredentialStore store = new CredentialStore(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText url = new EditText(this);
        url.setHint("https://homeassistant.local:8123");
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        EditText token = new EditText(this);
        token.setHint("Long-lived access token");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(url);
        form.addView(token);
        new AlertDialog.Builder(this)
                .setTitle("Home Assistant login")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        store.save(url.getText().toString().trim(), token.getText().toString().trim());
                        toast("Credentials saved.");
                        refreshStatus();
                    } catch (Exception e) {
                        toast("Could not save credentials.");
                    }
                }).show();
    }

    private void doNewConversation() {
        getSharedPreferences("tv_voice_settings_signals", MODE_PRIVATE)
                .edit().putBoolean("request_new_conversation", true).apply();
        toast("New conversation will start on the voice screen.");
    }

    private void toggleWakeWord(boolean on) {
        OverlayPreferences prefs = new OverlayPreferences(this);
        if (!on) {
            prefs.setWakeWord(false);
            WakeWordStartup.clearReminder(this);
            stopService(new Intent(this, WakeWordService.class));
            toast("Wake word off.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, WAKE_MIC_REQUEST);
            return;
        }
        prefs.setWakeWord(true);
        try {
            startForegroundService(new Intent(this, WakeWordService.class)
                    .setAction(WakeWordService.ACTION_START));
            toast("Wake word on.");
        } catch (RuntimeException e) {
            prefs.setWakeWord(false);
            toast("Could not start wake word.");
        }
    }

    private void chooseSensitivity(OverlayPreferences prefs) {
        String[] labels = {"TV playback", "Normal", "Quiet"};
        String[] values = {OverlayPreferences.NOISE_TV, OverlayPreferences.NOISE_NORMAL, OverlayPreferences.NOISE_QUIET};
        new AlertDialog.Builder(this)
                .setTitle("Microphone sensitivity")
                .setItems(labels, (d, which) -> {
                    prefs.setNoise(values[which]);
                    showCategory(selectedCategory);
                }).show();
    }

    private void chooseMicChecks(OverlayPreferences prefs) {
        String[] items = {
                prefs.microphoneDisabled() ? "Re-enable microphone" : "Disable microphone on this TV",
                prefs.allowUnverifiedMic() ? "Turn override OFF" : "Allow unverified mic (override)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Microphone input checks")
                .setItems(items, (d, which) -> {
                    if (which == 0) prefs.setMicrophoneDisabled(!prefs.microphoneDisabled());
                    else prefs.setAllowUnverifiedMic(!prefs.allowUnverifiedMic());
                    showCategory(selectedCategory);
                }).show();
    }

    private void toggleTvEvents(boolean on) {
        OverlayPreferences prefs = new OverlayPreferences(this);
        if (!on) {
            prefs.setTvEvents(false);
            stopService(new Intent(this, TvEventsService.class));
            toast("TV alerts off.");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Grant display-over-apps permission first.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
            return;
        }
        prefs.setTvEvents(true);
        try {
            startForegroundService(new Intent(this, TvEventsService.class)
                    .setAction(TvEventsService.ACTION_START));
            toast("TV alerts enabled.");
        } catch (RuntimeException e) {
            prefs.setTvEvents(false);
            toast("Could not start TV alerts.");
        }
    }

    private void chooseVolume(OverlayPreferences prefs) {
        String[] items = {"50%", "75%", "100%"};
        int[] values = {50, 75, 100};
        new AlertDialog.Builder(this)
                .setTitle("Announcement volume")
                .setItems(items, (d, which) -> {
                    prefs.setSpokenAnnouncementVolume(values[which]);
                    showCategory(selectedCategory);
                }).show();
    }

    private void showAlertStatus() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        String msg = "Preference: " + (prefs.tvEvents() ? "Enabled" : "Off")
                + "\nThis does not prove the scoped receiver is connected."
                + "\nOpen the voice screen for live status.";
        new AlertDialog.Builder(this).setTitle("TV alert status").setMessage(msg)
                .setPositiveButton("OK", null).show();
    }

    private void chooseCameraMode(OverlayPreferences prefs) {
        String[] items = {"Hardware", "Software"};
        new AlertDialog.Builder(this)
                .setTitle("Live camera decoder")
                .setItems(items, (d, which) -> {
                    prefs.setCameraMode(which == 0 ? LiveCameraMode.HARDWARE : LiveCameraMode.SOFTWARE);
                    showCategory(selectedCategory);
                }).show();
    }

    private void showScreensaverInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Screensaver")
                .setMessage("Detailed Frigate URL, camera list, size and fit options remain available from the voice screen settings. Toggle the dashboard here; open the voice screen for full configuration.")
                .setPositiveButton("OK", null).show();
    }

    private void doLaunchOverlay() {
        try {
            startForegroundService(new Intent(this, VoiceOverlayService.class));
            toast("Overlay requested.");
        } catch (Exception e) {
            toast("Could not start overlay.");
        }
    }

    private void doOverlayPrefs() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        String[] skins = {"Copper", "Aurora", "Signal", "Classic"};
        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setItems(skins, (d, which) -> {
                    String skin = OverlayPreferences.SKIN_GLASS;
                    if (which == 1) skin = OverlayPreferences.SKIN_AURORA;
                    else if (which == 2) skin = OverlayPreferences.SKIN_SIGNAL;
                    else if (which == 3) skin = OverlayPreferences.SKIN_CLASSIC;
                    prefs.setSkin(skin);
                    recreate();
                }).show();
    }

    private void doCheckUpdates() {
        toast("Update check runs from the voice screen. Open Kellie's Voice to check.");
    }

    private void doInstallUpdate() {
        toast("Install a previously downloaded update from the voice screen.");
    }

    private void doForget() {
        new AlertDialog.Builder(this)
                .setTitle("Forget Home Assistant login?")
                .setMessage("Deletes the saved URL and encrypted token from this TV. Wake word and TV alerts will be turned off.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Forget", (d, w) -> {
                    OverlayPreferences prefs = new OverlayPreferences(this);
                    prefs.setWakeWord(false);
                    WakeWordStartup.clearReminder(this);
                    stopService(new Intent(this, WakeWordService.class));
                    prefs.setTvEvents(false);
                    stopService(new Intent(this, TvEventsService.class));
                    new CredentialStore(this).clear();
                    toast("Saved connection deleted.");
                    refreshStatus();
                    showCategory(selectedCategory);
                }).show();
    }

    private void refreshStatus() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean configured = false;
        try { configured = new CredentialStore(this).load() != null; } catch (Exception ignored) {}
        statusCredentials.setText(configured ? "Credentials  •  Saved" : "Credentials  •  None");
        statusCredentials.setTextColor(configured ? 0xFF6FCF97 : colors.muted);

        boolean listening = prefs.wakeWord() && WakeWordService.isListening();
        String wake = !prefs.wakeWord() ? "Off" : listening ? "Listening" : "Stopped";
        statusWake.setText("Wake word  •  " + wake);
        statusWake.setTextColor(listening ? 0xFF6FCF97 : colors.muted);

        statusAlerts.setText("TV alerts  •  " + (prefs.tvEvents() ? "Enabled" : "Off"));
        statusAlerts.setTextColor(prefs.tvEvents() ? 0xFF6FCF97 : colors.muted);
    }

    private TextView statusChip(String base) {
        TextView chip = makeText(base, 13, colors.text, false);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.menuButton);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), colors.stroke);
        chip.setBackground(bg);
        return chip;
    }

    private TextView makeText(String text, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), 1));
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == WAKE_MIC_REQUEST && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) {
            toggleWakeWord(true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
