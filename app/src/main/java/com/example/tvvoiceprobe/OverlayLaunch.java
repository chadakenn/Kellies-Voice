package com.example.tvvoiceprobe;

/**
 * Same-process result for the voice overlay. A broadcast would be exported
 * on Android TV builds older than API 33, and another app could send it.
 */
final class OverlayLaunch {
    interface Observer { void onResult(boolean failed); }

    private static volatile Observer observer;

    private OverlayLaunch() { }

    static void set(Observer next) { observer = next; }

    static void clear(Observer current) {
        if (observer == current) observer = null;
    }

    static void deliver(boolean failed) {
        Observer current = observer;
        if (current != null) current.onResult(failed);
    }
}
