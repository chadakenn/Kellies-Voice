package com.example.tvvoiceprobe;

/**
 * Energy detector used only while auto-stop is on. Thresholds match the
 * previous in-loop behavior, including the TV-mode calibration window.
 */
public final class EnergyVad {
    private long observedMs;
    private long voiceMs;
    private long quietMs;
    private boolean heardUtterance;
    private double backgroundRms = 0.002;

    /** Already-sent wake/follow-up PCM counts as speech and elapsed capture. */
    public void noteLead(long milliseconds) {
        if (milliseconds <= 0) return;
        heardUtterance = true;
        // Separate buffered segments can both be sent in a single run.
        // Do not erase an earlier segment's duration when a second arrives.
        observedMs += milliseconds;
        quietMs = 0L;
    }

    /** @return true when this chunk should end the turn. */
    public boolean observe(byte[] pcm, int length, String noiseMode) {
        if (pcm == null || length <= 0) return false;
        long chunkMs = Math.max(1, length / 32);
        observedMs += chunkMs;
        double power = 0d;
        int samples = 0;
        for (int sample = 0; sample + 1 < length && sample + 1 < pcm.length; sample += 2) {
            short value = (short) ((pcm[sample] & 255) | (pcm[sample + 1] << 8));
            double normalized = value / 32768.0;
            power += normalized * normalized;
            samples++;
        }
        double rms = Math.sqrt(power / Math.max(1, samples));
        boolean television = "tv".equals(noiseMode);
        boolean quietRoom = "quiet".equals(noiseMode);
        if (television && observedMs < 600) {
            backgroundRms = backgroundRms * 0.8 + rms * 0.2;
            return false;
        }
        if (!heardUtterance && rms < backgroundRms * 2.2) {
            backgroundRms = backgroundRms * 0.96 + rms * 0.04;
        }
        double floor = quietRoom ? 0.003 : television ? 0.01 : 0.0045;
        double times = quietRoom ? 1.9 : television ? 3.0 : 2.4;
        long needVoice = quietRoom ? 140 : television ? 280 : 180;
        // Wait longer locally; Home Assistant's server-side stt-vad-end remains primary.
        boolean voice = rms > Math.max(floor, backgroundRms * times);
        if (!heardUtterance) {
            voiceMs = voice ? voiceMs + chunkMs : Math.max(0, voiceMs - chunkMs);
            if (voiceMs >= needVoice) heardUtterance = true;
        } else {
            quietMs = voice ? 0 : quietMs + chunkMs;
        }
        return SpeechPause.fallback(heardUtterance, quietMs, observedMs, noiseMode);
    }
}
