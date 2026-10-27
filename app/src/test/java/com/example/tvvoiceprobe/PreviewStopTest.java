package com.example.tvvoiceprobe;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreviewStopTest {
    @Test public void timeoutReleasesTheRecorderBeforeTheStoppedCallback() {
        AtomicInteger releases = new AtomicInteger();
        AtomicInteger notifies = new AtomicInteger();
        PreviewStop stop = new PreviewStop(releases::incrementAndGet);
        stop.fail(false, () -> {
            assertEquals(1, releases.get());
            notifies.incrementAndGet();
        });
        assertEquals(1, notifies.get());
        stop.abandon();
        assertEquals(1, releases.get());
    }

    @Test public void abandonDuringReleaseDoesNotReleaseTwice() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch leave = new CountDownLatch(1);
        PreviewStop stop = new PreviewStop(() -> {
            releases.incrementAndGet();
            inside.countDown();
            await(leave);
        });
        Thread failing = new Thread(() -> stop.fail(false, () -> { }));
        failing.start();
        assertTrue(inside.await(2, TimeUnit.SECONDS));
        Thread abandoning = new Thread(stop::abandon);
        abandoning.start();
        assertTrue(failing.isAlive());
        leave.countDown();
        failing.join(2000);
        abandoning.join(2000);
        assertEquals(1, releases.get());
    }

    @Test public void aHandoffKeepsTheRecorder() {
        AtomicInteger releases = new AtomicInteger();
        PreviewStop stop = new PreviewStop(releases::incrementAndGet);
        stop.requestHandoff();
        stop.fail(false, () -> { throw new AssertionError("notified during handoff"); });
        assertEquals(0, releases.get());
        stop.transferred();
        stop.releaseNow();
        assertEquals(0, releases.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
