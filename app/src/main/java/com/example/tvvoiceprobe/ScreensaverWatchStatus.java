package com.example.tvvoiceprobe;

/**
 * In-process diagnostics for Google TV ambient mode. No private addresses,
 * credentials, camera names or audio data are recorded.
 */
final class ScreensaverWatchStatus {
    private static volatile boolean running;
    private static volatile String lastEvent = "No Android DREAMING_STARTED event received";
    private static volatile String lastOverlay = "Not attempted";
    private static volatile String settingsSync = "HA settings sync not started";
    private static volatile String sourceSync = "No HA source update received";

    private ScreensaverWatchStatus() { }

    static void serviceRunning(boolean value) { running = value; }
    static boolean running() { return running; }
    static void event(String label) { lastEvent = label; }
    static String lastEvent() { return lastEvent; }
    static void overlay(String label) { lastOverlay = label; }
    static String lastOverlay() { return lastOverlay; }
    static void settingsSync(String label) { settingsSync = label; }
    static String settingsSync() { return settingsSync; }
    static void sourceSync(String label) { sourceSync = label; }
    static String sourceSync() { return sourceSync; }
}
