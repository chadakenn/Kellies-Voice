package com.example.tvvoiceprobe;

import android.media.AudioRecord;

/**
 * The microphone Assist should keep reading.
 * A null recorder means the preview mic could not be transferred. Opening a
 * new one then misses whatever arrived between the two recorders.
 */
public final class OpenCapture {
    public final AudioRecord recorder;
    public final int rate;
    public final byte[] lead16k;
    public final PcmCapture pcm;

    public OpenCapture(AudioRecord recorder, int rate, byte[] lead16k, PcmCapture pcm) {
        this.recorder = recorder;
        this.rate = rate == PcmCapture.RATE_48K ? PcmCapture.RATE_48K : PcmCapture.RATE_16K;
        this.lead16k = lead16k == null ? new byte[0] : lead16k;
        this.pcm = pcm == null ? new PcmCapture(this.rate) : pcm;
    }

    public boolean sameRecorder() { return recorder != null; }

    public static OpenCapture gap(byte[] lead) {
        return new OpenCapture(null, PcmCapture.RATE_16K, lead, null);
    }
}
