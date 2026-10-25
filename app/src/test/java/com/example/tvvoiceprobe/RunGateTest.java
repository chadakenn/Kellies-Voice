package com.example.tvvoiceprobe;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunGateTest {
    @Test public void cancelAfterHandoffDoesNotStartAndReleasesTheHandedOffRecorder() throws Exception {
        RunGate gate = new RunGate();
        FakeRecorder handed = new FakeRecorder();
        FakeRecorder newer = new FakeRecorder();
        FakeFocus focus = new FakeFocus();
        gate.open(7);
        assertTrue(gate.assignHandler(7, 4));
        assertTrue(gate.arm(7, 4));
        gate.acquire(7, focus::acquire);

        CountDownLatch claimReturned = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger oldCleanupCleared = new AtomicInteger();
        Thread capture = new Thread(() -> {
            claimReturned.countDown();
            await(cancelled);
            boolean started = gate.start(7, 4, handed::startRecording);
            if (!started) handed.release();
            gate.releaseFocusIfOwner(7, 4, () -> oldCleanupCleared.incrementAndGet());
            assertFalse(gate.endAudio(7, 4, () -> handed.sent.incrementAndGet()));
        });
        capture.start();
        assertTrue(claimReturned.await(2, TimeUnit.SECONDS));
        gate.cancel(7, focus::release);
        gate.open(8);
        assertTrue(gate.assignHandler(8, 9));
        assertTrue(gate.arm(8, 9));
        gate.acquire(8, focus::acquire);
        assertTrue(gate.start(8, 9, newer::startRecording));
        cancelled.countDown();
        capture.join(2000);

        assertEquals(0, handed.starts.get());
        assertEquals(1, handed.releases.get());
        assertEquals(0, handed.sent.get());
        assertEquals(1, newer.starts.get());
        assertTrue(gate.isRecording(8, 9));
        assertEquals(2, focus.acquires.get());
        assertEquals(1, focus.releases.get());
        assertEquals(0, oldCleanupCleared.get());
    }

    @Test public void cancelBetweenValidationAndSendDoesNotUseTheNewHandler() throws Exception {
        RunGate gate = new RunGate();
        FakeRecorder socket = new FakeRecorder();
        gate.open(7);
        gate.assignHandler(7, 4);
        gate.arm(7, 4);
        assertTrue(gate.start(7, 4, socket::startRecording));

        CountDownLatch validated = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        Thread capture = new Thread(() -> {
            assertTrue(gate.isRecording(7, 4));
            validated.countDown();
            await(cancelled);
            assertFalse(gate.send(7, 4, () -> socket.sent.incrementAndGet()));
        });
        capture.start();
        assertTrue(validated.await(2, TimeUnit.SECONDS));
        gate.stopAudio(7);
        gate.cancel(7, () -> { });
        gate.open(8);
        gate.assignHandler(8, 9);
        gate.arm(8, 9);
        assertTrue(gate.start(8, 9, () -> { }));
        assertTrue(gate.send(8, 9, () -> socket.sent.addAndGet(9)));
        cancelled.countDown();
        capture.join(2000);
        assertEquals(9, socket.sent.get());
        assertTrue(gate.isRecording(8, 9));
    }

    @Test public void staleCleanupCannotClearANewerRunAfterATrueSnapshot() throws Exception {
        RunGate gate = new RunGate();
        FakeFocus focus = new FakeFocus();
        gate.open(7);
        gate.assignHandler(7, 4);
        gate.arm(7, 4);
        gate.acquire(7, focus::acquire);
        assertTrue(gate.start(7, 4, () -> { }));

        CountDownLatch sawOwner = new CountDownLatch(1);
        CountDownLatch replaced = new CountDownLatch(1);
        AtomicInteger cleared = new AtomicInteger();
        Thread oldFinally = new Thread(() -> {
            assertTrue(gate.owns(7, 4));
            sawOwner.countDown();
            await(replaced);
            gate.releaseFocusIfOwner(7, 4, () -> cleared.incrementAndGet());
        });
        oldFinally.start();
        assertTrue(sawOwner.await(2, TimeUnit.SECONDS));
        gate.cancel(7, focus::release);
        gate.open(8);
        gate.assignHandler(8, 9);
        gate.arm(8, 9);
        gate.acquire(8, focus::acquire);
        assertTrue(gate.start(8, 9, () -> { }));
        replaced.countDown();
        oldFinally.join(2000);

        assertEquals(0, cleared.get());
        assertTrue(gate.isRecording(8, 9));
        assertTrue(gate.focusHeld());
        assertEquals(1, focus.releases.get());
        assertEquals(2, focus.acquires.get());
    }

    @Test public void stopAndCancelBlockUntilStartLeavesTheGate() throws Exception {
        RunGate gate = new RunGate();
        gate.open(7);
        gate.assignHandler(7, 4);
        gate.arm(7, 4);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch leave = new CountDownLatch(1);
        Thread starting = new Thread(() -> gate.start(7, 4, () -> {
            inside.countDown();
            await(leave);
        }));
        starting.start();
        assertTrue(inside.await(2, TimeUnit.SECONDS));
        CountDownLatch cancelDone = new CountDownLatch(1);
        Thread cancelling = new Thread(() -> {
            gate.cancel(7, () -> { });
            cancelDone.countDown();
        });
        cancelling.start();
        assertFalse(cancelDone.await(150, TimeUnit.MILLISECONDS));
        leave.countDown();
        assertTrue(cancelDone.await(2, TimeUnit.SECONDS));
        starting.join(2000);
        assertFalse(gate.start(7, 4, () -> { }));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static final class FakeRecorder {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger releases = new AtomicInteger();
        final AtomicInteger sent = new AtomicInteger();

        void startRecording() { starts.incrementAndGet(); }

        void release() { releases.incrementAndGet(); }
    }

    private static final class FakeFocus {
        final AtomicInteger acquires = new AtomicInteger();
        final AtomicInteger releases = new AtomicInteger();

        void acquire() { acquires.incrementAndGet(); }

        void release() { releases.incrementAndGet(); }
    }
}
