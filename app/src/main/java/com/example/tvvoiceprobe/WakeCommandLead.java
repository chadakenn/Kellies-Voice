package com.example.tvvoiceprobe;

/**
 * Audio recorded after Okay Nabu, before Assist opens its own microphone.
 * At most two seconds of 16 kHz PCM16. Nothing recorded before detection
 * is stored here, and a discarded buffer is never uploaded.
 */
public final class WakeCommandLead {
    /** 16 kHz, 16-bit mono, two seconds. */
    public static final int MAX_BYTES = 16000 * 2 * 2;

    private static boolean active;
    private static boolean stop;
    private static boolean ready;
    private static int length;
    private static byte[] data = new byte[MAX_BYTES];

    private WakeCommandLead() { }

    public static synchronized void begin() {
        active = true;
        stop = false;
        ready = false;
        length = 0;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized boolean isReady() {
        return ready;
    }

    public static synchronized boolean stopRequested() {
        return stop;
    }

    public static synchronized void requestStop() {
        stop = true;
        WakeCommandLead.class.notifyAll();
    }

    public static synchronized void append(short[] samples, int count) {
        if (!active || stop || samples == null || count <= 0) return;
        int samplesToStore = Math.min(count, (MAX_BYTES - length) / 2);
        for (int i = 0; i < samplesToStore; i++) {
            short value = samples[i];
            data[length++] = (byte) value;
            data[length++] = (byte) (value >> 8);
        }
        if (length >= MAX_BYTES) stop = true;
    }

    public static synchronized void finish() {
        if (!active) return;
        active = false;
        ready = true;
        WakeCommandLead.class.notifyAll();
    }

    /** Drop the buffer. It must not be sent to Home Assistant. */
    public static synchronized void discard() {
        active = false;
        ready = false;
        stop = false;
        length = 0;
        WakeCommandLead.class.notifyAll();
    }

    public static byte[] await(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        synchronized (WakeCommandLead.class) {
            while (active && !ready) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) break;
                try {
                    WakeCommandLead.class.wait(left);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!ready) return new byte[0];
            byte[] copy = java.util.Arrays.copyOf(data, length);
            ready = false;
            stop = false;
            length = 0;
            return copy;
        }
    }

    static synchronized void reset() {
        active = false;
        stop = false;
        ready = false;
        length = 0;
    }
}
