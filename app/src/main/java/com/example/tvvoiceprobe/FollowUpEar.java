package com.example.tvvoiceprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioRecord;

/**
 * Listens until someone speaks. The capture that started the listen owns its
 * buffer. Claiming it waits until that reader parks, then either keeps the
 * same recorder or reports that a new one would leave a gap.
 */
public final class FollowUpEar {
    private final Context context;
    private Capture live;

    public FollowUpEar(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean listen(int timeoutMs, String noiseMode, Runnable onSpeech, Runnable onMiss) {
        stop();
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        AudioRecord opened = AssistCapture.open();
        if (opened == null) return false;
        try {
            opened.startRecording();
            if (opened.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                AssistCapture.release(opened);
                return false;
            }
        } catch (RuntimeException ignored) {
            AssistCapture.release(opened);
            return false;
        }
        int rate = AssistCapture.rateOf(opened);
        Capture capture = new Capture(opened, rate, noiseMode,
                Math.min(15000, Math.max(5000, timeoutMs)), onSpeech, onMiss);
        synchronized (this) { live = capture; }
        Thread thread = new Thread(capture, "kellie-follow-up");
        thread.start();
        return true;
    }

    public void stop() {
        Capture capture;
        synchronized (this) {
            capture = live;
            live = null;
        }
        if (capture != null) capture.abandon();
    }

    public OpenCapture claimOpen() {
        Capture capture;
        synchronized (this) {
            capture = live;
            live = null;
        }
        if (capture == null) return OpenCapture.gap(new byte[0]);
        return capture.handoff();
    }

    private static final class Capture implements Runnable {
        private final Object ownership = new Object();
        private final FollowUpTake audio;
        private final String noiseMode;
        private final int timeoutMs;
        private final Runnable onSpeech;
        private final Runnable onMiss;
        private final int rate;
        private AudioRecord recorder;
        private final PreviewStop stopping;
        private boolean workerDone;
        private boolean ownershipSettled;

        Capture(AudioRecord recorder, int rate, String noiseMode, int timeoutMs,
                Runnable onSpeech, Runnable onMiss) {
            this.recorder = recorder;
            this.rate = rate;
            this.noiseMode = noiseMode;
            this.timeoutMs = timeoutMs;
            this.onSpeech = onSpeech;
            this.onMiss = onMiss;
            this.audio = new FollowUpTake(rate);
            this.stopping = new PreviewStop(this::releaseRecorderLocked);
        }

        @Override public void run() {
            boolean signaled = false;
            try {
                byte[] chunk = new byte[rate == PcmCapture.RATE_48K ? 9600 : 3200];
                int elapsed = 0;
                while (!audio.isStopped()) {
                    if (!signaled && elapsed >= timeoutMs) {
                        miss();
                        return;
                    }
                    if (signaled && elapsed >= timeoutMs + 20000) return;
                    int read;
                    try {
                        read = recorder.read(chunk, 0, chunk.length);
                    } catch (RuntimeException ignored) {
                        miss();
                        return;
                    }
                    if (read <= 0) {
                        miss();
                        return;
                    }
                    elapsed += Math.max(1, (read / Math.max(1, rate / 1000)) / 2);
                    boolean voiced = FollowUpSession.voiced(chunk, read, noiseMode);
                    boolean latched = audio.accept(chunk, read, voiced);
                    if (latched && !signaled && !audio.isStopped()) {
                        signaled = true;
                        if (onSpeech != null) onSpeech.run();
                    }
                }
            } finally {
                audio.park();
                synchronized (ownership) {
                    workerDone = true;
                    ownership.notifyAll();
                    if (stopping.handoffPending()) {
                        long deadline = System.currentTimeMillis() + 2000;
                        while (!ownershipSettled) {
                            long left = deadline - System.currentTimeMillis();
                            if (left <= 0) break;
                            try {
                                ownership.wait(left);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                stopping.abandon();
            }
        }

        OpenCapture handoff() {
            stopping.requestHandoff();
            audio.requestStop();
            FollowUpTake.Result result = audio.claim(1500);
            AudioRecord kept = result.workerFinished() ? detach() : null;
            if (kept == null) releaseAndSettle();
            PcmCapture continued = kept == null ? new PcmCapture(rate) : result.capture();
            return new OpenCapture(kept, rate, result.audio(), continued);
        }

        void abandon() {
            audio.requestStop();
            stopping.abandon();
            synchronized (ownership) {
                ownershipSettled = true;
                ownership.notifyAll();
            }
            audio.claim(0);
        }

        private void miss() {
            stopping.fail(audio.isStopped(), onMiss);
        }

        private AudioRecord detach() {
            synchronized (ownership) {
                long deadline = System.currentTimeMillis() + 1500;
                while (!workerDone) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) break;
                    try {
                        ownership.wait(left);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                AudioRecord current = workerDone && !stopping.released() ? recorder : null;
                if (current != null) {
                    recorder = null;
                    stopping.transferred();
                }
                ownershipSettled = true;
                ownership.notifyAll();
                return current;
            }
        }

        private void releaseAndSettle() {
            stopping.abandon();
            synchronized (ownership) {
                ownershipSettled = true;
                ownership.notifyAll();
            }
        }

        private void releaseRecorderLocked() {
            AudioRecord current = recorder;
            recorder = null;
            AssistCapture.release(current);
        }
    }
}
