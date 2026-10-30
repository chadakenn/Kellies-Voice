package com.example.tvvoiceprobe;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Process-local screensaver state. Not a preference and not written to disk. */
final class ScreensaverSignals {
    interface Listener { void ourDreamChanged(); }

    private static volatile boolean ourDream;
    private static volatile boolean dismissed;
    private static volatile Listener listener;
    private static final List<Runnable> REFRESHERS = new CopyOnWriteArrayList<>();

    private ScreensaverSignals() { }

    static boolean ourDream() { return ourDream; }
    static boolean dismissed() { return dismissed; }

    static void setListener(Listener value) { listener = value; }

    static void listen(Runnable refresh) {
        if (refresh != null) REFRESHERS.add(refresh);
    }

    static void unlisten(Runnable refresh) { REFRESHERS.remove(refresh); }

    /** Home Assistant changed the camera list or size. Open dashboards rebuild. */
    public static void settingsChanged() {
        for (Runnable refresh : REFRESHERS) refresh.run();
    }

    static void dreamOpened() {
        ourDream = true;
        Listener current = listener;
        if (current != null) current.ourDreamChanged();
    }

    static void dreamClosed() {
        ourDream = false;
        Listener current = listener;
        if (current != null) current.ourDreamChanged();
    }

    static void dismiss() { dismissed = true; }

    static void screensaverStopped() { dismissed = false; }
}
