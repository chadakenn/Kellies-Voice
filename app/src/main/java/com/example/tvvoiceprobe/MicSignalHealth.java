package com.example.tvvoiceprobe;

/**
 * Strict zero-signal warning for the diagnostic test only.
 * A declared BUILTIN_MIC or an initialized AudioRecord does not guarantee
 * any speech capture. This never denies microphone permission or changes TALK.
 */
public final class MicSignalHealth {
    private long digitalSilenceMs;
    private boolean warned;

    /** @return true once when at least 3 seconds of near-zero PCM were received. */
    public boolean observe(short[] samples, int length, int sampleRate) {
        if (samples == null || sampleRate <= 0 || length <= 0) return false;
        int used = Math.min(length, samples.length);
        if (used <= 0) return false;
        boolean onlyNearZero = true;
        for (int i = 0; i < used; i++) {
            if (samples[i] > 1 || samples[i] < -1) {
                onlyNearZero = false;
                break;
            }
        }
        if (!onlyNearZero) {
            digitalSilenceMs = 0;
            return false;
        }
        digitalSilenceMs += Math.max(1, (used * 1000L) / sampleRate);
        if (!warned && digitalSilenceMs >= 3000) {
            warned = true;
            return true;
        }
        return false;
    }
}
