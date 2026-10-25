package com.example.tvvoiceprobe;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Opens 16 kHz first, then 48 kHz. Samples are converted by {@link PcmCapture},
 * the same filter PR #6 uses, not by a separate average.
 */
public final class AssistCapture {
    private AssistCapture() {}

    public static AudioRecord open() {
        AudioRecord recorder = open(16000, 6400);
        return recorder != null ? recorder : open(48000, 19200);
    }

    public static int rateOf(AudioRecord recorder) {
        if (recorder == null) return 0;
        return recorder.getSampleRate() == 48000 ? 48000 : 16000;
    }

    public static AudioRecord open(int sampleRate, int minimumBytes) {
        int minimum = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) return null;
        AudioRecord recorder = null;
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minimum * 2, minimumBytes));
            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) return recorder;
        } catch (RuntimeException ignored) {
            // Fall through and release anything the constructor returned.
        }
        release(recorder);
        return null;
    }

    public static void release(AudioRecord recorder) {
        if (recorder == null) return;
        try { recorder.stop(); } catch (RuntimeException ignored) { }
        try { recorder.release(); } catch (RuntimeException ignored) { }
    }
}
