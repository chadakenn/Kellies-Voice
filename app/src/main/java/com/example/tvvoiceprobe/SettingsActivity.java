package com.example.tvvoiceprobe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Full-screen premium Settings surface for Android TV.
 * Uses the existing copper/charcoal (or selected theme) palette.
 * All functional actions are delegated back to VoiceActivity so existing
 * preference logic, dialogs and safety checks remain unchanged.
 */
public class SettingsActivity extends Activity {
    private static final String ACTION_EXTRA = "com.example.tvvoiceprobe.SETTINGS_ACTION";

    private AppPalette colors;
    private LinearLayout contentArea;
    private TextView statusConnection;
    private TextView statusWake;
    private TextView statusAlerts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        colors = AppPalette.forSkin(new OverlayPreferences(this).skin());
        buildUi();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        root.setBackgroundColor(colors.screen);

        // Header
        TextView kicker = makeText("KELLIE'S VOICE", 14, colors.accent, true);
        root.addView(kicker);
        TextView title = makeText("Settings", 36, colors.text, true);
        title.setPadding(0, dp(4), 0, dp(8));
        root.addView(title);

        // Status row
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, 0, 0, dp(20));
        statusConnection = statusChip("Connection");
        statusWake = statusChip("Wake word");
        statusAlerts = statusChip("TV alerts");
        statusRow.addView(statusConnection);
        statusRow.addView(spacer(12));
        statusRow.addView(statusWake);
        statusRow.addView(spacer(12));
        statusRow.addView(statusAlerts);
        root.addView(statusRow);

        // Main two-column area
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setWeightSum(2);

        // Left: categories
        ScrollView catScroll = new ScrollView(this);
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.VERTICAL);
        categories.setPadding(0, 0, dp(20), 0);

        String[][] cats = {
            {"Home Assistant", "Connection, pairing and conversation"},
            {"Voice & microphone", "Speech pickup, follow-ups and input"},
            {"Overlay & appearance", "Voice popup, size and shortcuts"},
            {"Alerts & cameras", "TV notifications and live video"},
            {"Screensaver", "Frigate dashboard and cameras"},
            {"Updates & account", "Versions and saved login"}
        };
        View first = null;
        for (String[] cat : cats) {
            View card = categoryCard(cat[0], cat[1]);
            categories.addView(card);
            if (first == null) first = card;
        }
        catScroll.addView(categories);
        LinearLayout.LayoutParams catParams = new LinearLayout.LayoutParams(0, -1, 1);
        columns.addView(catScroll, catParams);

        // Right: content
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setPadding(dp(20), 0, 0, 0);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, -1, 1);
        columns.addView(contentArea, contentParams);

        root.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));

        // Footer hint
        TextView hint = makeText("Use D-pad to move • OK to select • Back to return", 15, colors.muted, false);
        hint.setPadding(0, dp(16), 0, 0);
        root.addView(hint);

        setContentView(root);
        if (first != null) first.requestFocus();
        showWelcome();
    }

    private void showWelcome() {
        contentArea.removeAllViews();
        TextView welcome = makeText("Select a category", 22, colors.text, true);
        contentArea.addView(welcome);
        TextView desc = makeText("All existing settings are available. Connection, wake-word and alert status update above.", 16, colors.muted, false);
        desc.setPadding(0, dp(8), 0, 0);
        contentArea.addView(desc);
    }

    private View categoryCard(String title, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setFocusable(true);
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.menuButton);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(2), colors.stroke);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        TextView t = makeText(title, 20, colors.text, true);
        card.addView(t);
        TextView d = makeText(detail, 14, colors.muted, false);
        d.setPadding(0, dp(4), 0, 0);
        card.addView(d);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable shape = (GradientDrawable) v.getBackground();
            shape.setStroke(hasFocus ? dp(4) : dp(2), hasFocus ? colors.focus : colors.stroke);
            shape.setColor(hasFocus ? colors.menuFocus : colors.menuButton);
            if (hasFocus) showCategoryOptions(title);
        });
        card.setOnClickListener(v -> showCategoryOptions(title));
        return card;
    }

    private void showCategoryOptions(String category) {
        contentArea.removeAllViews();
        TextView heading = makeText(category, 24, colors.accent, true);
        contentArea.addView(heading);

        String[] options = optionsFor(category);
        for (String opt : options) {
            View row = optionRow(opt, category);
            contentArea.addView(row);
        }
    }

    private String[] optionsFor(String category) {
        OverlayPreferences prefs = new OverlayPreferences(this);
        switch (category) {
            case "Home Assistant":
                return new String[] {
                    "Connect / reconnect Home Assistant",
                    "Edit Home Assistant address & token",
                    "Start new conversation",
                    "Manage TV layout from Home Assistant: " + (prefs.haManage() ? "ON" : "OFF")
                };
            case "Voice & microphone":
                return new String[] {
                    "Microphone sensitivity",
                    "Follow-up listening",
                    "Microphone input checks",
                    "Test microphone hardware",
                    "Wake word: " + (!prefs.wakeWord() ? "OFF" : WakeWordService.isListening() ? "LISTENING" : "STOPPED")
                };
            case "Overlay & appearance":
                return new String[] {
                    "Open floating voice overlay",
                    "Appearance, position and controls"
                };
            case "Alerts & cameras":
                return new String[] {
                    "TV notifications & camera popups",
                    "Spoken announcements",
                    "Announcement voice volume",
                    "Test a spoken announcement",
                    "TV alert status & connection",
                    "Live camera decoder"
                };
            case "Screensaver":
                return new String[] {
                    "Frigate screensaver settings",
                    "Preview screensaver"
                };
            case "Updates & account":
                return new String[] {
                    "Check for updates",
                    "Install downloaded update",
                    "Forget saved connection"
                };
            default:
                return new String[0];
        }
    }

    private View optionRow(String label, String category) {
        TextView row = makeText(label, 18, colors.text, false);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.menuButton);
        bg.setCornerRadius(dp(8));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable shape = (GradientDrawable) v.getBackground();
            shape.setColor(hasFocus ? colors.menuFocus : colors.menuButton);
            shape.setStroke(hasFocus ? dp(3) : 0, colors.focus);
        });
        row.setOnClickListener(v -> dispatch(category, label));
        return row;
    }

    private void dispatch(String category, String label) {
        String action = mapAction(category, label);
        if (action == null) return;
        Intent intent = new Intent(this, VoiceActivity.class);
        intent.putExtra(ACTION_EXTRA, action);
        // VoiceActivity is singleTask. Starting it clears activities above it.
        // After the action (or dialog dismiss) VoiceActivity re-launches this Activity
        // so the premium full-screen UI remains the host and Back returns here.
        startActivity(intent);
    }

    private String mapAction(String category, String label) {
        if (label.startsWith("Connect / reconnect")) return "connect";
        if (label.startsWith("Edit Home Assistant")) return "configure";
        if (label.startsWith("Start new conversation")) return "newConversation";
        if (label.startsWith("Manage TV layout")) return "chooseHaLayout";
        if (label.startsWith("Microphone sensitivity")) return "chooseMicSensitivity";
        if (label.startsWith("Follow-up listening")) return "chooseFollowUp";
        if (label.startsWith("Microphone input checks")) return "chooseMicrophoneDetection";
        if (label.startsWith("Test microphone")) return "testMic";
        if (label.startsWith("Wake word")) return "chooseWakeWord";
        if (label.startsWith("Open floating")) return "launchOverlay";
        if (label.startsWith("Appearance")) return "showOverlayPreferences";
        if (label.startsWith("TV notifications")) return "chooseTvNotifications";
        if (label.startsWith("Spoken announcements")) return "toggleSpoken";
        if (label.startsWith("Announcement voice volume")) return "chooseAnnouncementVolume";
        if (label.startsWith("Test a spoken")) return "testSpoken";
        if (label.startsWith("TV alert status")) return "showTvAlertStatus";
        if (label.startsWith("Live camera")) return "chooseCameraMode";
        if (label.startsWith("Frigate screensaver")) return "showScreensaverSettings";
        if (label.startsWith("Preview screensaver")) return "previewScreensaver";
        if (label.startsWith("Check for updates")) return "chooseUpdates";
        if (label.startsWith("Install downloaded")) return "promptInstallUpdate";
        if (label.startsWith("Forget saved")) return "confirmForget";
        return null;
    }

    private void refreshStatus() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean configured = false;
        try {
            configured = new CredentialStore(this).load() != null;
        } catch (Exception ignored) {}
        statusConnection.setText(configured ? "Credentials  •  Saved" : "Connection  •  Not configured");
        statusConnection.setTextColor(configured ? 0xFF6FCF97 : colors.muted);

        boolean wakeOn = prefs.wakeWord();
        boolean listening = WakeWordService.isListening();
        String wake = !wakeOn ? "Off" : listening ? "Listening" : "Stopped";
        statusWake.setText("Wake word  •  " + wake);
        statusWake.setTextColor(listening ? 0xFF6FCF97 : colors.muted);

        boolean alerts = prefs.tvEvents();
        statusAlerts.setText("TV alerts  •  " + (alerts ? "Enabled" : "Off"));
        statusAlerts.setTextColor(alerts ? 0xFF6FCF97 : colors.muted);
    }

    private TextView statusChip(String base) {
        TextView chip = makeText(base, 14, colors.text, false);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.menuButton);
        bg.setCornerRadius(dp(20));
        chip.setBackground(bg);
        return chip;
    }

    private TextView makeText(String text, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
