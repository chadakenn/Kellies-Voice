package com.example.tvvoiceprobe;

/**
 * Main-thread ownership for a visible TV pairing window.
 *
 * A callback from a previous discovery attempt is only allowed to update
 * the UI when its captured generation is still the current one. The same
 * guard is used by PairingSetupActivity and the unit tests.
 *
 * No network listener, microphone, credentials or state persistence here.
 */
final class PairingGenerationGuard {
    private long generation;

    /** Invalidates every outstanding callback, even across cancel/restart. */
    long advance() {
        // Java long overflow is not a realistic UI concern, but do not let
        // the initial unused value (0) become an accepted generation.
        generation++;
        if (generation == 0L) generation++;
        return generation;
    }

    boolean isCurrent(long captured) {
        return captured != 0L && captured == generation;
    }
}
