package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RetryTimingTest {
    @Test public void jitterStaysInsideTheCap() {
        assertEquals(2000L, RetryTiming.withJitter(2000L, 30_000L, 0d));
        assertEquals(2400L, RetryTiming.withJitter(2000L, 30_000L, 1d));
        assertEquals(30_000L, RetryTiming.withJitter(30_000L, 30_000L, 1d));
        assertTrue(RetryTiming.withJitter(16_000L, 30_000L, 0.5d) > 16_000L);
    }
}
