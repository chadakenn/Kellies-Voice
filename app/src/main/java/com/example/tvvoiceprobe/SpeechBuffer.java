package com.example.tvvoiceprobe;

import java.util.Arrays;

/**
 * Keeps every microphone byte, including a short read's leftover sample.
 * 16 kHz conversion happens once, through {@link PcmCapture}, when the buffer is taken.
 */
public final class SpeechBuffer {
    private final int rate;
    private byte[] rolling = new byte[0];
    private byte[] kept;
    private boolean latched;
    private int streak;

    public SpeechBuffer(int rate) {
        this.rate = rate == PcmCapture.RATE_48K ? PcmCapture.RATE_48K : PcmCapture.RATE_16K;
    }

    public boolean latched() { return latched; }

    public boolean add(byte[] chunk, int length, boolean voiced) {
        if (chunk == null || length <= 0) return latched;
        int count = Math.min(length, chunk.length);
        if (count <= 0) return latched;
        byte[] piece = Arrays.copyOf(chunk, count);
        if (!latched) {
            rolling = concat(rolling, piece);
            int cap = prerollBytes();
            if (rolling.length > cap) {
                rolling = Arrays.copyOfRange(rolling, rolling.length - cap, rolling.length);
            }
            streak = voiced ? streak + 1 : 0;
            if (streak >= 2) {
                latched = true;
                kept = rolling;
            }
        } else {
            kept = concat(kept, piece);
            int cap = rate * 2 * 8;
            if (kept.length > cap) kept = Arrays.copyOfRange(kept, kept.length - cap, kept.length);
        }
        return latched;
    }

    /** @return 16 kHz speech, or an empty array when nobody spoke. */
    public byte[] to16k() {
        return to16k(new PcmCapture(rate));
    }

    public byte[] to16k(PcmCapture capture) {
        byte[] source = latched && kept != null ? kept : new byte[0];
        if (source.length == 0 || !containsSpeech(source, rate) || capture == null) return new byte[0];
        byte[] dst = new byte[source.length + 2];
        int written = capture.process(source, source.length, dst);
        return written <= 0 ? new byte[0] : Arrays.copyOf(dst, written);
    }

    public void clear() {
        rolling = new byte[0];
        kept = null;
        latched = false;
        streak = 0;
    }

    public static boolean containsSpeech(byte[] pcm, int rate) {
        if (pcm == null || pcm.length < 2) return false;
        int window = rate == PcmCapture.RATE_48K ? 9600 : 3200;
        if (pcm.length <= window) return FollowUpSession.voiced(pcm, pcm.length, "normal");
        for (int offset = 0; offset + window <= pcm.length; offset += window) {
            if (FollowUpSession.voiced(pcm, offset, window, "normal")) return true;
        }
        return FollowUpSession.voiced(pcm, pcm.length - window, window, "normal");
    }

    private int prerollBytes() {
        return rate == PcmCapture.RATE_48K ? 38400 : 12800;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] both = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, both, left.length, right.length);
        return both;
    }
}
