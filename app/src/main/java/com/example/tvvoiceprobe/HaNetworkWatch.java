package com.example.tvvoiceprobe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;

/**
 * Fires once when a lost default network comes back. The first callback after
 * registration is ignored, so attaching the watcher does not restart a healthy
 * socket. Callers must still replace a socket that is open, because that
 * socket is bound to the network that was just lost.
 */
final class HaNetworkWatch {
    private final Context context;
    private final Handler handler;
    private final Runnable onReady;
    private ConnectivityManager.NetworkCallback callback;
    private boolean lost;

    HaNetworkWatch(Context context, Handler handler, Runnable onReady) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.onReady = onReady;
    }

    void start() {
        if (callback != null) return;
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return;
        ConnectivityManager.NetworkCallback created = new ConnectivityManager.NetworkCallback() {
            @Override public void onLost(Network network) { lost = true; }

            @Override public void onAvailable(Network network) {
                if (!lost) return;
                lost = false;
                onReady.run();
            }
        };
        try {
            manager.registerDefaultNetworkCallback(created, handler);
            callback = created;
        } catch (RuntimeException ignored) { }
    }

    void stop() {
        ConnectivityManager.NetworkCallback current = callback;
        callback = null;
        lost = false;
        if (current == null) return;
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return;
        try { manager.unregisterNetworkCallback(current); }
        catch (RuntimeException ignored) { }
    }
}
