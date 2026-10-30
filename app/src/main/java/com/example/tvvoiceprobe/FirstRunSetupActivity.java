package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * D-pad-first, explicitly consented first-run wizard for the isolated beta.
 * No microphone, wake listener, login or TV-alert service is started on entry.
 * Stable app is untouched; wizard state belongs to the .debug package.
 */
public final class FirstRunSetupActivity extends Activity {
    private static final String PREFS = "kv_first_run_setup";
    private static final String COMPLETED = "completed_v1";
    private static final String STEP = "current_step_v1";
    private static final int REQUEST_MIC = 41;
    private static final int REQUEST_OVERLAY = 42;
    private int step;
    private boolean returningFromExternalSettings;
    private LinearLayout page;
    private AppPalette palette;

    public static boolean needsSetup(Activity activity) {
        return (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && !activity.getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(COMPLETED, false);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // This screen is test-only until first-run permission/alert hardware QA.
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            finish();
            return;
        }
        step = state != null ? state.getInt("step", 0)
                : getSharedPreferences(PREFS, MODE_PRIVATE).getInt(STEP, 0);
        step = Math.max(0, Math.min(3, step));
        palette = AppPalette.forSkin(new OverlayPreferences(this).skin());
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("step", step);
        super.onSaveInstanceState(out);
    }

    @Override protected void onResume() {
        super.onResume();
        // Android TV does not always deliver activity results from Special App Access.
        // Refresh visible permissions without automatically enabling alerts.
        if (returningFromExternalSettings || step == 1) {
            returningFromExternalSettings = false;
            render();
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        result.setPadding(dp(3), dp(7), dp(3), dp(7));
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }

    private Button button(String value, Runnable click) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setSingleLine(false);
        b.setFocusable(true);
        b.setTextColor(palette.text);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(palette.menuButton);
        bg.setStroke(dp(1), palette.stroke);
        GradientDrawable selected = new GradientDrawable();
        selected.setCornerRadius(dp(14));
        selected.setColor(palette.menuFocus);
        selected.setStroke(dp(3), palette.accent);
        b.setBackground(new android.graphics.drawable.StateListDrawable() {{
            addState(new int[]{android.R.attr.state_focused}, selected);
            addState(new int[]{}, bg);
        }});
        b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        page.addView(b, params);
        return b;
    }

    private boolean hasTvLogin() {
        return new TvRenewableLoginVault(this).hasActiveLogin();
    }

    private void render() {
        palette = AppPalette.forSkin(new OverlayPreferences(this).skin());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(35), dp(22), dp(35), dp(22));
        root.setBackgroundColor(palette.screen);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("KELLIE'S VOICE  /  WELCOME", 18, palette.accent, true));
        root.addView(text("SETUP  " + (step + 1) + " OF 4", 15, palette.muted, false));

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));

        Button focusButton;
        switch (step) {
            case 0:
                page.addView(text("Your TV. Your voice. Your Home Assistant.",
                        30, palette.text, true));
                page.addView(text("A guided setup for secure Home Assistant pairing, "
                        + "Android microphone permission, and optional TV camera alerts. "
                        + "Use your remote's arrows and OK button. No mic or alerts "
                        + "will start without your permission.", 19, palette.muted, false));
                focusButton = button("START SETUP", () -> advance(1));
                break;
            case 1:
                page.addView(text("Connect Home Assistant", 30, palette.text, true));
                page.addView(text(hasTvLogin() ? "Secure TV login: ACTIVATED"
                                : "Secure TV login: NOT YET PAIRED",
                        20, hasTvLogin() ? palette.accent : palette.muted, true));
                page.addView(text("On the TV, start secure pairing, compare the eight digits "
                        + "with Home Assistant, confirm with your physical remote, "
                        + "then approve the dedicated TV login in the HA owner panel. "
                        + "The TV can be discovered and verified over local HTTP, "
                        + "but automatic renewable Home Assistant login currently "
                        + "needs an HTTPS HA address (Cloud or local HTTPS). "
                        + "A separate non-admin TV account can control all "
                        + "Home Assistant entities.", 18, palette.muted, false));
                focusButton = button("OPEN SECURE TV PAIRING", () ->
                        startActivity(new Intent(this, PairingV3QaActivity.class)));
                button("CONTINUE TO MICROPHONE", () -> advance(2));
                break;
            case 2:
                page.addView(text("Microphone permission", 30, palette.text, true));
                boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
                page.addView(text(mic ? "Android microphone: GRANTED"
                                : "Android microphone: NOT GRANTED",
                        20, mic ? palette.accent : palette.muted, true));
                page.addView(text("Android needs microphone permission only when you "
                        + "choose to talk. Granting it does not start recording, "
                        + "turn on the wake word, or listen in the background.",
                        18, palette.muted, false));
                focusButton = button(mic ? "MICROPHONE GRANTED" : "GRANT MICROPHONE PERMISSION", () -> {
                    if (!mic) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_MIC);
                    else render();
                });
                button("OPEN ANDROID APP PERMISSIONS", this::openAppSettings);
                button("CONTINUE TO TV ALERTS", () -> advance(3));
                break;
            default:
                page.addView(text("Camera popups and TV alerts", 30, palette.text, true));
                OverlayPreferences preferences = new OverlayPreferences(this);
                boolean allowed = Settings.canDrawOverlays(this);
                page.addView(text("Display over other apps: " + (allowed ? "GRANTED" : "NOT GRANTED"),
                        19, allowed ? palette.accent : palette.muted, true));
                page.addView(text("TV alerts: " + (preferences.tvEvents() ? "ENABLED" : "OFF"),
                        19, palette.muted, false));
                page.addView(text("Alerts are optional. The Beta needs Android overlay "
                        + "permission AND its own device registered under the normal "
                        + "Kellie's Voice integration in Home Assistant. Secure v3 "
                        + "login alone does not register an alerts device.",
                        18, palette.muted, false));
                focusButton = button(allowed ? "OVERLAY PERMISSION GRANTED"
                        : "GRANT DISPLAY OVER OTHER APPS", () -> {
                    if (Settings.canDrawOverlays(this)) render();
                    else openOverlaySettings();
                });
                button(preferences.tvEvents() ? "TV ALERTS ENABLED" : "ENABLE TV ALERTS (OPTIONAL)",
                        this::enableAlerts);
                button("SHOW HOME ASSISTANT TV-DEVICE PAIRING STEPS", this::showAlertPairing);
                button("FINISH SETUP", this::complete);
                break;
        }

        // Explicit alternatives: never trap a user behind permissions or pairing.
        if (step > 0) button("BACK", () -> advance(step - 1));
        button("SKIP SETUP FOR NOW", this::complete);
        focusButton.requestFocus();
    }

    private void advance(int next) {
        step = next;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(STEP, step).apply();
        render();
    }

    private void complete() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(COMPLETED, true).remove(STEP).apply();
        finish();
    }

    private void openAppSettings() {
        try {
            returningFromExternalSettings = true;
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            returningFromExternalSettings = false;
            new AlertDialog.Builder(this).setMessage(
                    "Open Android TV Settings > Apps > Kellie's Voice Beta > Permissions.")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void openOverlaySettings() {
        try {
            returningFromExternalSettings = true;
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQUEST_OVERLAY);
        } catch (Exception ignored) {
            returningFromExternalSettings = false;
            openAppSettings();
        }
    }

    private void showAlertPairing() {
        OverlayPreferences p = new OverlayPreferences(this);
        new AlertDialog.Builder(this)
                .setTitle("Register Beta TV for alerts")
                .setMessage("1. Finish the wizard and keep Kellie's Voice Beta open.\n"
                        + "2. In the Beta, Settings > Home Assistant > Manage TV layout "
                        + "from Home Assistant: ON.\n"
                        + "3. Home Assistant > Settings > Devices & services > "
                        + "add the NORMAL Kellie's Voice integration for this Beta TV.\n"
                        + "4. Return to Alerts & cameras > TV alert status > Resume.\n\n"
                        + "Beta pairing ID (not a credential):\n" + p.deviceId()
                        + "\n\nDo not use the separate Kellie's Voice v3 QA integration "
                        + "as the alert device.")
                .setPositiveButton("OK", null).show();
    }

    private void enableAlerts() {
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Android overlay access required")
                    .setMessage("First select Grant Display over other apps. "
                            + "Android must approve this permission before TV alerts can run.")
                    .setPositiveButton("Open permission", (d, w) -> openOverlaySettings())
                    .setNegativeButton("Later", null).show();
            return;
        }
        if (!hasTvLogin()) {
            new AlertDialog.Builder(this)
                    .setTitle("Home Assistant login needed")
                    .setMessage("Complete secure TV pairing before enabling TV alerts. "
                            + "You can skip this for now.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        OverlayPreferences prefs = new OverlayPreferences(this);
        prefs.setTvEvents(true);
        try {
            startForegroundService(new Intent(this, TvEventsService.class)
                    .setAction(TvEventsService.ACTION_RESUME));
            new AlertDialog.Builder(this).setTitle("TV alerts requested")
                    .setMessage("Receiver enabled. To receive camera popups and announcements, "
                            + "also register this Beta TV in the normal Kellie's Voice "
                            + "Home Assistant integration.")
                    .setPositiveButton("Pairing instructions",
                            (d, w) -> showAlertPairing())
                    .setNegativeButton("Close", (d, w) -> render()).show();
        } catch (RuntimeException ignored) {
            prefs.setTvEvents(false);
            new AlertDialog.Builder(this).setMessage(
                    "Android blocked the TV alert receiver. Try again from Alerts & cameras.")
                    .setPositiveButton("OK", (d, w) -> render()).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                       int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_MIC) render();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            returningFromExternalSettings = false;
            render();
        }
    }

    @Override public void onBackPressed() {
        if (step > 0) advance(step - 1);
        else complete();
    }
}
