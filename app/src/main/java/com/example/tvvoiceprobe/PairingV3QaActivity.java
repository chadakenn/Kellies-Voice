package com.example.tvvoiceprobe;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;

/**
 * Home Assistant-first owner-approved pairing, with no TV URL or token typing.
 *
 * Never starts the microphone or changes an existing working connection on open. The 120-second
 * LAN window starts only after pressing START on the actual TV remote.
 */
public final class PairingV3QaActivity extends Activity {
    private PairingV3LanWindow window;
    private TextView status;
    private TextView code;
    private Button start;
    private Button confirm;
    private int generation;

    private static final int BG = 0xff181d19;
    private static final int FG = 0xfff5f0e7;
    private static final int MUTED = 0xffc0bdb1;
    private static final int GOLD = 0xffe1bb74;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(38, 26, 38, 26);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        scroll.addView(root);
        setContentView(scroll);

        TextView heading = text("KELLIE'S VOICE  |  SECURE TV PAIRING", 25, GOLD);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(heading);
        root.addView(text("SECURE HOME ASSISTANT PAIRING", 16, MUTED));
        root.addView(text(
            "Start a short local pairing window, compare the same eight-digit "
            + "code in Home Assistant and on this TV, then press OK. "
            + "After TV OK, approve this TV in the signed-in HA owner panel "
            + "and keep this screen visible for the encrypted receipt. "
            + "Login is optional; microphone and existing alerts remain unchanged.",
            18, FG));

        status = text(
            "Ready. Nothing is listening on the network. "
            + "Press START only when you are ready to pair this TV.",
            18, MUTED);
        root.addView(status);
        code = text("", 44, FG);
        code.setGravity(Gravity.CENTER);
        code.setTypeface(Typeface.DEFAULT_BOLD);
        code.setLetterSpacing(.12f);
        code.setVisibility(View.GONE);
        root.addView(code);

        start = button("START SECURE PAIRING");
        start.setOnClickListener(v -> startWindow());
        root.addView(start);

        confirm = button("EIGHT DIGITS MATCH  •  TV REMOTE OK");
        confirm.setEnabled(false);
        confirm.setOnClickListener(v -> {
            PairingV3LanWindow current = window;
            if (current == null || !confirm.isEnabled()) return;
            boolean approved = current.approveFromPhysicalRemote();
            confirm.setEnabled(false);
            if (approved) {
                status.setText(
                    "TV REMOTE OK VERIFIED. Leave this screen open. "
                    + "In Home Assistant, use the owner approval page then "
                    + "Submit in the original dialog BEFORE the two-minute "
                    + "window expires. Await encrypted owner acknowledgement. "
                    + "If the owner opted into login, wait for activation.");
                code.setVisibility(View.VISIBLE);
            } else {
                status.setText("Pairing expired. Start a new attempt.");
                code.setVisibility(View.GONE);
            }
        });
        root.addView(confirm);

        Button cancel = button("CLOSE SETUP  /  KEEP CURRENT LOGIN");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);
        start.requestFocus();
    }

    private void startWindow() {
        if (window != null) {
            // Repeated D-pad OK cannot silently create a second listener.
            return;
        }
        stopWindow();
        final int attempt = ++generation;
        start.setEnabled(false);
        confirm.setEnabled(false);
        code.setText("");
        code.setVisibility(View.GONE);
        status.setText("Starting Android network discovery...");
        try {
            PairingV3LanWindow opened = PairingV3LanWindow.start(
                this,
                value -> {
                    if (attempt != generation || isFinishing() || isDestroyed()) return;
                    code.setText(value);
                    code.setVisibility(View.VISIBLE);
                    status.setText(
                        "Compare ALL eight digits with Home Assistant. "
                        + "Only press TV REMOTE OK when the codes match.");
                    confirm.setEnabled(true);
                    confirm.requestFocus();
                },
                stateMessage -> {
                    if (attempt != generation || isFinishing() || isDestroyed()) return;
                    if (stateMessage.startsWith(
                            "SECURE OWNER APPROVAL VERIFIED")
                            || stateMessage.startsWith("TV LOGIN ACTIVATED")) {
                        // Tear down the now-consumed pairing listener and
                        // its pending expiry callback. Keep success visible.
                        stopWindow();
                    }
                    status.setText(stateMessage);
                },
                stopReason -> {
                    if (attempt != generation || isFinishing() || isDestroyed()) return;
                    stopWindow();
                    status.setText(stopReason);
                    start.setEnabled(true);
                    start.requestFocus();
                });
            // Guard an immediate asynchronous NSD failure/Activity closure.
            if (attempt != generation || isFinishing() || isDestroyed()) {
                opened.close();
                return;
            }
            window = opened;
        } catch (IOException | RuntimeException error) {
            stopWindow();
            // Keep the underlying failure visible instead of briefly flashing.
            // Never print credentials: this QA path handles none.
            status.setText("V3 START FAILED: " + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage())
                    + ". Check TV network and try again.");
            start.setEnabled(true);
            start.requestFocus();
        }
    }

    private void stopWindow() {
        generation++;
        PairingV3LanWindow existing = window;
        window = null;
        if (existing != null) existing.close();
        if (start != null) start.setEnabled(true);
        if (confirm != null) confirm.setEnabled(false);
        if (code != null) {
            code.setText("");
            code.setVisibility(View.GONE);
        }
    }

    @Override protected void onStop() {
        boolean wasRunning = window != null;
        stopWindow();
        if (wasRunning && status != null) {
            status.setText("Pairing stopped because this screen closed. "
                    + "Return here and press START again.");
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopWindow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        stopWindow();
        super.onBackPressed();
    }

    private TextView text(String value, int sp, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(color);
        label.setTextSize(sp);
        label.setPadding(0, 11, 0, 11);
        return label;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setFocusable(true);
        return button;
    }
}
