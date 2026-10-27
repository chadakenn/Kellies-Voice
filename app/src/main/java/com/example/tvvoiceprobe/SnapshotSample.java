package com.example.tvvoiceprobe;

/** Power-of-two decode sample so a camera frame is not shrunk far below the popup. */
public final class SnapshotSample {
    private SnapshotSample() { }

    public static int size(int sourceWidth, int sourceHeight, int targetWidth) {
        int target = Math.max(1, targetWidth);
        int largest = Math.max(1, Math.max(sourceWidth, sourceHeight));
        int sample = 1;
        while (sample < 16 && largest / (sample * 2) >= target) sample *= 2;
        return sample;
    }
}
