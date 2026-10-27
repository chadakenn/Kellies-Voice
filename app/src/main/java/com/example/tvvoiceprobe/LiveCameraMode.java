package com.example.tvvoiceprobe;

/** Explicit per-TV decoder safety mode, separate from microphone and Assist. */
public final class LiveCameraMode {
    public static final String SOFTWARE = "software";
    public static final String SNAPSHOT = "snapshot";
    public static final String HARDWARE = "hardware";
    private LiveCameraMode() { }

    public static String normalize(String mode) {
        if (SNAPSHOT.equals(mode) || HARDWARE.equals(mode)) return mode;
        return SOFTWARE; // Unknown/missing preference cannot enable hardware.
    }

    public static boolean requestsStream(String mode) {
        return !SNAPSHOT.equals(normalize(mode));
    }

    public static boolean softwareOnly(String mode) {
        return SOFTWARE.equals(normalize(mode));
    }

    public static String label(String mode) {
        switch (normalize(mode)) {
            case SNAPSHOT: return "Snapshots only";
            case HARDWARE: return "Hardware (advanced)";
            default: return "Safe software (default)";
        }
    }
}
