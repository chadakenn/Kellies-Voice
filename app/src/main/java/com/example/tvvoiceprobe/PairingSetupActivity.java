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
        root.addView(message("Discovery preview for developers", 19, Color.WHITE));
        root.addView(message("Start a temporary 2-minute LAN discovery window. "
                + "The TV will advertise a real, read-only endpoint so we can test "
                + "Home Assistant detection. No login is created and no Home Assistant "
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

        Button closeButton = new Button(this);
        closeButton.setText("CANCEL / BACK");
        closeButton.setOnClickListener(v -> finish());
        root.addView(closeButton);

        setContentView(scroll);
        startButton.requestFocus();
    }

    private void begin() {
        stopWindow();
        try {
            window = PairingDiscoveryWindow.start(this, () -> {
                window = null;
                state.setText("Discovery ended. The listener is closed.");
                startButton.setEnabled(true);
            });
            startButton.setEnabled(false);
            state.setText("Waiting for Home Assistant discovery (120 seconds). "
                    + "Local read-only endpoint available. No authentication occurs.");
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
