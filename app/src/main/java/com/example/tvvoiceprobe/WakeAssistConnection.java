package com.example.tvvoiceprobe;

import android.content.Context;

/**
 * Pre-authenticate Home Assistant while the user has explicitly armed the
 * on-device wake listener. This owns NO microphone and sends NO audio.
 *
 * A wake-triggered overlay may take the already-authenticated WebSocket.
 * Closing the overlay closes that session, and the armed wake service may
 * warm a new one for the next command. Never started by boot or update.
 */
final class WakeAssistConnection {
    private static HaAssistClient standby;

    private static final HaAssistClient.Events QUIET = new HaAssistClient.Events() {
        @Override public void status(String ignored) { }
        @Override public void transcript(String ignored) { }
        @Override public void answer(String ignored) { }
        @Override public void ttsUrl(String ignored) { }
    };

    private WakeAssistConnection() { }

    static synchronized void preconnect(Context context) {
        if (standby != null || !WakeWordService.isListening()) return;
        OverlayPreferences prefs = new OverlayPreferences(context);
        if (!prefs.wakeWord() || prefs.microphoneDisabled()) return;
        CredentialStore.Connection login;
        try {
            login = new CredentialStore(context).load();
        } catch (Exception ignored) {
            return;
        }
        if (login == null) return;
        HaAssistClient created = new HaAssistClient(context.getApplicationContext(), QUIET);
        standby = created;
        created.connect(login.url, login.token);
    }

    /** Take only for a real wake detection. A normal overlay makes its own connection. */
    static synchronized HaAssistClient take(HaAssistClient.Events receiver) {
        HaAssistClient client = standby;
        standby = null;
        if (client != null) client.setEvents(receiver);
        return client;
    }

    static synchronized void stop() {
        HaAssistClient old = standby;
        standby = null;
        if (old != null) old.shutdown();
    }
}
