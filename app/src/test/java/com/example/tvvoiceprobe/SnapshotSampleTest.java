package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SnapshotSampleTest {
    @Test public void sampleFollowsThePopupWidth() {
        assertEquals(1, SnapshotSample.size(800, 600, 1280));
        assertEquals(2, SnapshotSample.size(2560, 1440, 1280));
        assertEquals(2, SnapshotSample.size(1920, 1080, 960));
        assertEquals(4, SnapshotSample.size(2560, 1440, 400));
    }
}
