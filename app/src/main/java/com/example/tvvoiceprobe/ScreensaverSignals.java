package com.example.tvvoiceprobe;

/** Process-local screensaver state. Not a preference and not written to disk. */
final class ScreensaverSignals {
    interface Listener { void ourDreamChanged(); }

    private static volatile boolean ourDream;
    private static volatile boolean dismissed;
    private static volatile Listener listener;

    private ScreensaverSignals() { }

    static boolean ourDream() { return ourDream; }
    static boolean dismissed() { return dismissed; }

    static void setListener(Listener value) { listener = value; }

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
