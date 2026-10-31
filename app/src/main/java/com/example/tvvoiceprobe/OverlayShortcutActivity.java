package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
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
    private BroadcastReceiver overlayEvents;
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
        TvRenewableLoginVault vault = new TvRenewableLoginVault(this);
        boolean configured = vault.hasActiveLogin();
        if (!vault.blocksManualFallback()) {
            try { configured = new CredentialStore(this).load() != null; }
            catch (Exception ignored) { configured = false; }
        }
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
        overlayEvents = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                handler.removeCallbacks(giveUp);
                unregisterOverlayEvents();
                if (VoiceOverlayService.ACTION_OVERLAY_FAILED.equals(intent.getAction())) {
                    Toast.makeText(OverlayShortcutActivity.this,
                            "Overlay didn't open. Opening Kellie's Voice.",
                            Toast.LENGTH_LONG).show();
                    startActivity(new Intent(OverlayShortcutActivity.this, VoiceActivity.class)
                            .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS"));
                }
                finish();
            }
        };
        IntentFilter filter = new IntentFilter(VoiceOverlayService.ACTION_OVERLAY_READY);
        filter.addAction(VoiceOverlayService.ACTION_OVERLAY_FAILED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(overlayEvents, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(overlayEvents, filter);
        }
    }

    private void unregisterOverlayEvents() {
        if (overlayEvents == null) return;
        try { unregisterReceiver(overlayEvents); } catch (Exception ignored) { }
        overlayEvents = null;
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterOverlayEvents();
        super.onDestroy();
    }
}
