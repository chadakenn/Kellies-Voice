package io.homeassistant.companion.android.microwakeword;

import android.util.Log;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * On-device wake word engine. Java port of the Home Assistant companion class
 * {@code MicroWakeWord}, Apache-2.0, https://github.com/home-assistant/android
 * The native methods are registered by the vendored JNI code against this class.
 *
 * Audio must be 16-bit PCM mono at 16 kHz. Not thread-safe.
 */
public final class MicroWakeWord implements Closeable {
    private static final int SAMPLE_RATE = 16000;
    private static volatile boolean libraryLoaded;
    private long nativeHandle;

    public MicroWakeWord(ByteBuffer modelBuffer, int featureStepSizeMs,
                         float probabilityCutoff, int slidingWindowSize) {
        if (featureStepSizeMs <= 0) {
            throw new IllegalArgumentException("featureStepSizeMs must be positive");
        }
        if (slidingWindowSize <= 0) {
            throw new IllegalArgumentException("slidingWindowSize must be positive");
        }
        if (probabilityCutoff < 0f || probabilityCutoff > 1f) {
            throw new IllegalArgumentException("probabilityCutoff must be in [0, 1]");
        }
        if (modelBuffer == null || !modelBuffer.isDirect()) {
            throw new IllegalArgumentException("modelBuffer must be a direct ByteBuffer");
        }
        ensureLibraryLoaded();
        nativeHandle = nativeCreate(modelBuffer, SAMPLE_RATE, featureStepSizeMs,
                probabilityCutoff, slidingWindowSize);
        if (nativeHandle == 0L) {
            throw new IllegalStateException("Failed to create native MicroWakeWord engine");
        }
    }

    public static void ensureLibraryLoaded() {
        if (!libraryLoaded) {
            synchronized (MicroWakeWord.class) {
                if (!libraryLoaded) {
                    System.loadLibrary("microwakeword");
                    libraryLoaded = true;
                    Log.d("MicroWakeWord", "Loaded microwakeword native library");
                }
            }
        }
    }

    /** @return true when the wake word was detected in this or a recent frame */
    public boolean processAudio(short[] samples) {
        if (nativeHandle == 0L) throw new IllegalStateException("MicroWakeWord has been closed");
        return nativeProcessAudio(nativeHandle, samples);
    }

    public void reset() {
        if (nativeHandle == 0L) throw new IllegalStateException("MicroWakeWord has been closed");
        nativeReset(nativeHandle);
    }

    @Override public void close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0L;
        }
    }

    private static native long nativeCreate(ByteBuffer modelBuffer, int sampleRate,
                                            int featureStepSizeMs, float probabilityCutoff,
                                            int slidingWindowSize);

    private static native boolean nativeProcessAudio(long handle, short[] samples);

    private static native void nativeReset(long handle);

    private static native void nativeDestroy(long handle);
}
