package com.example.tvvoiceprobe;

/**
 * One follow-up capture's audio. Claim freezes it. A later add cannot change
 * the bytes already taken or land in a newer capture.
 */
public final class FollowUpTake {
    private final Object lock = new Object();
    private final SpeechBuffer buffer;
    private final PcmCapture capture;
    private boolean stop;
    private boolean parked;
    private boolean taken;
    private byte[] claimed = new byte[0];

    public FollowUpTake(int rate) {
        buffer = new SpeechBuffer(rate);
        capture = new PcmCapture(bufferRate(rate));
    }

    public boolean accept(byte[] chunk, int length, boolean voiced) {
        synchronized (lock) {
            if (taken) return false;
            return buffer.add(chunk, length, voiced);
        }
    }

    public boolean isStopped() {
        synchronized (lock) { return stop; }
    }

    public void requestStop() {
        synchronized (lock) {
            stop = true;
            lock.notifyAll();
        }
    }

    public void park() {
        synchronized (lock) {
            parked = true;
            lock.notifyAll();
        }
    }

    /** Freeze this capture. {@code waitMs} waits for the reader to park first. */
    public Result claim(long waitMs) {
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + Math.max(0, waitMs);
            while (!parked) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) break;
                try {
                    lock.wait(left);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!taken) {
                taken = true;
                stop = true;
                claimed = buffer.to16k(capture);
                buffer.clear();
            }
            return new Result(claimed, parked, capture);
        }
    }

    private static int bufferRate(int rate) {
        return rate == PcmCapture.RATE_48K ? PcmCapture.RATE_48K : PcmCapture.RATE_16K;
    }

    public static final class Result {
        private final byte[] audio;
        private final boolean workerFinished;
        private final PcmCapture capture;

        Result(byte[] audio, boolean workerFinished, PcmCapture capture) {
            this.audio = audio;
            this.workerFinished = workerFinished;
            this.capture = capture;
        }

        public byte[] audio() { return audio; }

        public PcmCapture capture() { return capture; }

        /** False means the reader had not parked, so a same-recorder handoff is not safe. */
        public boolean workerFinished() { return workerFinished; }
    }
}
