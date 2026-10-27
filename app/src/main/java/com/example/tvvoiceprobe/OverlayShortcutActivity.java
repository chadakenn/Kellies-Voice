package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Separate TV launcher entry for optional mapping to an onn ⭐ shortcut.
 * A standard launcher action cannot take over the OEM/Google remote mic key.
 */
public final class OverlayShortcutActivity extends Activity {
    private boolean started;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OverlayLaunch.Observer overlayEvents = failed -> {
        handler.removeCallbacks(giveUp);
        OverlayLaunch.clear(overlayEvents);
        if (failed) {
            Toast.makeText(this, "Overlay didn't open. Opening Kellie's Voice.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, VoiceActivity.class)
                    .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS"));
        }
        finish();
    };
    private final Runnable giveUp = () -> {
        unregisterOverlayEvents();
        Toast.makeText(this, "Overlay didn't open. Opening Kellie's Voice.",
                Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, VoiceActivity.class)
                .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS"));
        finish();
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView message = new TextView(this);
        message.setText("Opening Kellie's Voice...");
        message.setTextColor(0xffffffff);
        message.setTextSize(22);
        message.setPadding(45, 60, 45, 60);
        message.setBackgroundColor(0xff221713);
        setContentView(message);
    }

    @Override protected void onResume() {
        super.onResume();
        if (started) return;
        started = true;
        // Use a visible Activity to start the microphone-typed foreground
        // service. Android 14+ restricts starting it from the background.
        handler.post(this::launch);
    }

    private void launch() {
        if (isFinishing()) return;
        boolean configured = false;
        try { configured = new CredentialStore(this).load() != null; }
        catch (Exception ignored) { }
        if (!configured || !Settings.canDrawOverlays(this)
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "First open Kellie's Voice, then SETTINGS > Floating voice overlay",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, VoiceActivity.class)
                    .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS"));
            finish();
            return;
        }
        registerOverlayEvents();
        try {
            startForegroundService(new Intent(this, VoiceOverlayService.class));
            // Retain the visible activity until the service signals ready.
            handler.postDelayed(giveUp, 8000L);
        } catch (Exception e) {
            handler.removeCallbacks(giveUp);
            unregisterOverlayEvents();
            Toast.makeText(this, "Overlay not supported. Open Kellie's Voice.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, VoiceActivity.class)
                    .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS"));
            finish();
        }
    }

    private void registerOverlayEvents() {
        OverlayLaunch.set(overlayEvents);
    }

    private void unregisterOverlayEvents() {
        OverlayLaunch.clear(overlayEvents);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterOverlayEvents();
        super.onDestroy();
    }
}
