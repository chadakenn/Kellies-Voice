package com.example.tvvoiceprobe;

/**
 * Turns microphone PCM into the 16 kHz mono stream Home Assistant expects.
 * 16 kHz is copied. 48 kHz is low-passed, then kept as every third sample.
 * Leftover bytes and filter state survive short reads.
 */
public final class PcmCapture {
    public static final int RATE_16K = 16000;
    public static final int RATE_48K = 48000;
    private static final int TAPS = 15;
    /** Unity-gain lowpass. Cutoff is about 7.5 kHz at a 48 kHz input. */
    private static final int[] LOWPASS = {
            66, -83, -515, -803, 433, 3962, 8232, 10184, 8232, 3962, 433, -803, -515, -83, 66
    };

    private final int sourceRate;
    private final short[] delay = new short[TAPS];
    private int phase;
    private int held = -1;

    public PcmCapture(int sourceRate) {
        if (sourceRate != RATE_16K && sourceRate != RATE_48K) {
            throw new IllegalArgumentException("unsupported microphone rate");
        }
        this.sourceRate = sourceRate;
    }

    public static boolean supported(int sourceRate) {
        return sourceRate == RATE_16K || sourceRate == RATE_48K;
    }

    /** @return little-endian 16 kHz bytes written to {@code dst}. */
    public int process(byte[] src, int srcBytes, byte[] dst) {
        if (src == null || dst == null || srcBytes <= 0) return 0;
        int available = Math.min(srcBytes, src.length);
        int in = 0;
        int out = 0;
        if (held >= 0 && available > 0 && out + 1 < dst.length) {
            short sample = (short) (held | (src[0] << 8));
            in = 1;
            held = -1;
            out = emit(sample, dst, out);
        }
        while (in + 1 < available && out + 1 < dst.length) {
            short sample = (short) ((src[in] & 255) | (src[in + 1] << 8));
            in += 2;
            out = emit(sample, dst, out);
        }
        if (in < available) held = src[in] & 255;
        return out;
    }

    private int emit(short sample, byte[] dst, int out) {
        if (sourceRate == RATE_16K) {
            dst[out] = (byte) sample;
            dst[out + 1] = (byte) (sample >> 8);
            return out + 2;
        }
        System.arraycopy(delay, 1, delay, 0, TAPS - 1);
        delay[TAPS - 1] = sample;
        phase++;
        if (phase < 3) return out;
        phase = 0;
        long sum = 0;
        for (int i = 0; i < TAPS; i++) sum += (long) delay[i] * LOWPASS[i];
        int filtered = (int) ((sum + (sum >= 0 ? 16384 : -16384)) / 32768);
        if (filtered > 32767) filtered = 32767;
        if (filtered < -32768) filtered = -32768;
        dst[out] = (byte) filtered;
        dst[out + 1] = (byte) (filtered >> 8);
        return out + 2;
    }
}
