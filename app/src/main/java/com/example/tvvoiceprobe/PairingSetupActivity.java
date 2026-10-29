package com.example.tvvoiceprobe;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Deliberately explicit development screen for #193 discovery-stage QA.
 * It is NOT a production login screen and cannot install any credentials.
 */
public final class PairingSetupActivity extends Activity {
    private PairingDiscoveryWindow window;
    private TextView state;
    private Button startButton;
    private Button confirmButton;

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(48), dp(32), dp(48), dp(32));
        root.setBackgroundColor(0xff181512);
        scroll.addView(root);

        TextView title = message("HOME ASSISTANT PAIRING", 24, 0xffE9B390);
        root.addView(title);
        root.addView(message("Two-screen verification preview for developers", 19, Color.WHITE));
        root.addView(message("Start a temporary 2-minute LAN verification window. "
                + "The TV will exchange only temporary public keys with Home Assistant so we can test "
                + "the eight-digit code on both screens. No login is created and no Home Assistant "
                + "URL, token, authorization code, or microphone audio is exchanged.", 17, 0xffe7d7c9));
        root.addView(message("Automatic authorization is not implemented yet. "
                + "Keep using the existing Home Assistant login for normal TV operation.", 15,
                0xffcaa58b));
        state = message("Inactive. No pairing listener is running.", 18, 0xffE9B390);
        root.addView(state);

        startButton = new Button(this);
        startButton.setText("START DISCOVERY PREVIEW");
        startButton.setOnClickListener(v -> begin());
        root.addView(startButton);

        confirmButton = new Button(this);
        confirmButton.setText("THE EIGHT DIGITS MATCH - CONFIRM ON TV");
        confirmButton.setEnabled(false);
        confirmButton.setOnClickListener(v -> {
            if (window != null && window.approveFromTv()) {
                state.setText("TV remote approved this visual code. "
                        + "Go back to Home Assistant to verify the proof. "
                        + "This preview does NOT connect your account.");
                confirmButton.setEnabled(false);
            } else {
                state.setText("No active verification. Start a new discovery preview.");
                confirmButton.setEnabled(false);
            }
        });
        root.addView(confirmButton);

        Button closeButton = new Button(this);
        closeButton.setText("CANCEL / BACK");
        closeButton.setOnClickListener(v -> finish());
        root.addView(closeButton);

        setContentView(scroll);
        startButton.requestFocus();
    }

    private void begin() {
        stopWindow();
        confirmButton.setEnabled(false);
        try {
            window = PairingDiscoveryWindow.start(this, () -> {
                window = null;
                state.setText("Discovery ended. The listener is closed.");
                confirmButton.setEnabled(false);
                startButton.setEnabled(true);
            }, code -> {
                state.setText("COMPARE THE CODE BELOW WITH HOME ASSISTANT:\n\n"
                        + code + "\n\nOnly press Confirm if ALL eight digits match. "
                        + "Never accept a pairing request you did not start.");
                confirmButton.setEnabled(true);
                confirmButton.requestFocus();
            });
            startButton.setEnabled(false);
            state.setText("LAN discovery preview active for 120 seconds. "
                    + "Home Assistant authorization is not yet implemented.");
        } catch (Exception ignored) {
            stopWindow();
            state.setText("Could not start LAN discovery. Check local networking.");
            startButton.setEnabled(true);
        }
    }

    private void stopWindow() {
        if (window != null) {
            window.close();
            window = null;
        }
        if (confirmButton != null) confirmButton.setEnabled(false);
    }

    private TextView message(String value, int sp, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setPadding(0, 0, 0, dp(22));
        return text;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    /** Pairing lifetime is scoped to the visible screen, never reboot/standby. */
    @Override protected void onStop() {
        stopWindow();
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopWindow();
        super.onDestroy();
    }
}
