package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.homeassistant.companion.android.microwakeword.MicroWakeWord;

/**
 * Opt-in on-device "Okay Nabu" listener. It starts only from the settings
 * screen, never from boot or an app update, and it releases the microphone
 * before the voice overlay opens. Audio stays on the TV until that overlay
 * talks to Home Assistant.
 */
public final class WakeWordService extends Service {
    public static final String ACTION_START = WakeWordSession.ACTION_START;
    public static final String ACTION_STOP = WakeWordSession.ACTION_STOP;
    private static final String CHANNEL_ID = "ha_tv_wake_word";
    private static final int NOTICE_ID = 2251;
    private static final int FAILURE_NOTICE_ID = 2252;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK = 160;

    private final Object micLock = new Object();
    private volatile boolean running;
    private volatile String failureNotice;
    private Thread worker;
    private AudioRecord recorder;
    private static volatile WakeWordService current;

    /** Settings must show actual listener state, not just the persisted opt-in. */
    public static boolean isListening() {
        WakeWordService service = current;
        return service != null && service.running
                && service.worker != null && service.worker.isAlive();
    }

    /**
     * Take the microphone from the wake listener only after its AudioRecord
     * has been released. Diagnostics calls this on a background thread.
     * The activity and overlay must use {@link #requestHandoff} instead.
     */
    public static boolean handoffTo(String owner) {
        if (MicLease.acquire(owner)) return true;
        if (!MicLease.isWaiting(owner)) return false;
        WakeWordService service = current;
        if (service != null) service.stopRecorder();
        boolean held = MicLease.awaitHeld(owner, 1500L);
        if (!held) MicLease.release(owner);
        return held;
    }

    public interface Handoff {
        void accepted(Cancellation cancellation);
        void denied();
    }

    /** Cancels a handoff that has not started capture. Does not block. */
    public static final class Cancellation {
        private final String owner;
        private volatile boolean cancelled;
        private volatile boolean started;

        Cancellation(String owner) { this.owner = owner; }

        public boolean isCancelled() { return cancelled; }

        public void markStarted() { started = true; }

        public void cancel() {
            cancelled = true;
            if (!started) MicLease.release(owner);
        }
    }

    /**
     * Ask for the microphone without blocking the caller. The callback runs
     * on the caller thread when the lease is already free, otherwise on a
     * short background thread after the wake recorder has been released.
     */
    public static Cancellation requestHandoff(String owner, Handoff callback) {
        Cancellation cancellation = new Cancellation(owner);
        if (callback == null) return cancellation;
        if (MicLease.acquire(owner)) {
            callback.accepted(cancellation);
            return cancellation;
        }
        if (!MicLease.isWaiting(owner)) {
            callback.denied();
            return cancellation;
        }
        Thread waiter = new Thread(() -> {
            if (cancellation.isCancelled()) {
                MicLease.release(owner);
                return;
            }
            WakeWordService service = current;
            if (service != null) service.stopRecorder();
            boolean held = MicLease.awaitHeld(owner, 1500L);
            if (cancellation.isCancelled()) {
                MicLease.release(owner);
                return;
            }
            if (!held) {
                MicLease.release(owner);
                callback.denied();
                return;
            }
            callback.accepted(cancellation);
        }, "kellie-mic-handoff");
        waiter.setDaemon(true);
        waiter.start();
        return cancellation;
    }

    /**
     * Audio captured after Okay Nabu until Assist claims it, at most
     * {@link WakeCommandLead#MAX_MS}. Empty when no wake command is in progress.
     * Safe to call from the Assist capture thread. Do not call it from the UI.
     */
    public static byte[] claimCommandLead(long timeoutMs) {
        if (!WakeCommandLead.isActive() && !WakeCommandLead.isReady()) return new byte[0];
        WakeCommandLead.requestStop();
        WakeWordService service = current;
        if (service != null) service.stopRecorder();
        return WakeCommandLead.await(timeoutMs);
    }

    /** Drop a wake question that will not be sent. Does nothing if none is pending. */
    public static void abandonCommandLead() {
        if (!WakeCommandLead.isActive() && !WakeCommandLead.isReady()) return;
        WakeCommandLead.discard();
        WakeWordService service = current;
        if (service != null) service.stopRecorder();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        WakeAudioDucking.recover(this);
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action) || !allowed(action)) {
            // Don't leave Settings claiming ON after a denied explicit start.
            if (ACTION_START.equals(action)) new OverlayPreferences(this).setWakeWord(false);
            running = false;
            WakeAssistConnection.stop();
            releaseRecorder();
            MicLease.release(MicLease.WAKE);
            if (!VoiceOverlayService.isOpen()) WakeAudioDucking.end(this);
            // Timely stop of a rejected explicit start. Promoting a microphone
            // FGS without RECORD_AUDIO is forbidden on modern Android.
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        Notification notice = buildNotice();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTICE_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTICE_ID, notice);
            }
        } catch (RuntimeException rejected) {
            android.util.Log.w("KelliesWake", "Foreground microphone start rejected: "
                    + rejected.getClass().getSimpleName());
            // Android may reject an automatic foreground resume. Keep the
            // explicit saved ON preference so a visible launch can try again.
            if (ACTION_START.equals(action)) new OverlayPreferences(this).setWakeWord(false);
            else WakeWordStartup.showResumeReminder(this);
            running = false;
            WakeAssistConnection.stop();
            releaseRecorder();
            MicLease.release(MicLease.WAKE);
            if (!VoiceOverlayService.isOpen()) WakeAudioDucking.end(this);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        current = this;
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(this::listen, "kellie-wake-word");
            worker.start();
        }
        WakeWordStartup.clearReminder(this);
        // This WebSocket is authenticated now, before the user says Okay Nabu.
        // No STT run or microphone is opened by the warm connection.
        WakeAssistConnection.preconnect(this);
        return START_NOT_STICKY;
    }

    private boolean allowed(String action) {
        OverlayPreferences prefs = new OverlayPreferences(this);
        if (prefs.microphoneDisabled()) return false;
        if (!WakeWordSession.mayStart(prefs.wakeWord(), action,
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED,
                android.provider.Settings.canDrawOverlays(this))) return false;
        // Do not allow a wake listener without an HA login. The token remains
        // inside CredentialStore; no plaintext credentials enter wake logging.
        try {
            return new CredentialStore(this).load() != null
                    && MicrophoneRouting.preflight(this, prefs.allowUnverifiedMic()) == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void listen() {
        MicroWakeWord engine = null;
        OverlayPreferences prefs = new OverlayPreferences(this);
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            WakeWordConfig config = WakeWordConfig.parse(readAsset("wakeword/okay_nabu.json"));
            engine = new MicroWakeWord(loadModel("wakeword/" + config.model),
                    config.featureStepSizeMs, config.probabilityCutoff, config.slidingWindowSize);
            WakeWordSession session = new WakeWordSession();
            short[] chunk = new short[CHUNK];
            int permissionCheck = 0;
            while (running && prefs.wakeWord()) {
                // Android may revoke mic/overlay permission while the listener is armed.
                // Check each ~1s (10ms audio chunks), not only at service startup.
                if (++permissionCheck >= 100) {
                    permissionCheck = 0;
                    if (prefs.microphoneDisabled()) {
                        notifyFailure("Microphone capture is disabled on this TV. Wake word off.");
                        prefs.setWakeWord(false);
                        break;
                    }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED
                            || !android.provider.Settings.canDrawOverlays(this)) {
                        notifyFailure("Microphone or overlay permission was revoked. Wake word off.");
                        prefs.setWakeWord(false);
                        break;
                    }
                }
                if (!WakeWordSession.shouldCapture(true,
                        VoiceOverlayService.isOpen() || MicLease.heldByOther(MicLease.WAKE)
                                || MicLease.wakeShouldStop())) {
                    releaseRecorder();
                    MicLease.release(MicLease.WAKE);
                    sleep(200);
                    continue;
                }
                if (!MicLease.acquire(MicLease.WAKE)) {
                    releaseRecorder();
                    MicLease.release(MicLease.WAKE);
                    sleep(200);
                    continue;
                }
                if (MicLease.wakeShouldStop()) {
                    // The user pressed TALK while WAKE was about to open its
                    // recorder. Transfer the idle lease without opening it.
                    releaseRecorder();
                    MicLease.release(MicLease.WAKE);
                    sleep(20);
                    continue;
                }
                AudioRecord currentRecorder;
                synchronized (micLock) {
                    if (!ensureRecorder()) {
                        notifyFailure("The microphone could not open at 16 kHz. Wake word is off.");
                        prefs.setWakeWord(false);
                        break;
                    }
                    currentRecorder = recorder;
                }
                int read = currentRecorder.read(chunk, 0, CHUNK);
                if (MicLease.wakeShouldStop() || recorder != currentRecorder) {
                    releaseRecorder();
                    continue;
                }
                if (read < 0) {
                    // ERROR_DEAD_OBJECT or another capture failure must not spin
                    // indefinitely with a hot microphone-service notification.
                    notifyFailure("Microphone capture stopped unexpectedly. Wake word off.");
                    prefs.setWakeWord(false);
                    break;
                }
                if (read == 0) { sleep(30); continue; }
                short[] samples = read == CHUNK ? chunk : java.util.Arrays.copyOf(chunk, read);
                if (!session.onChunk(engine.processAudio(samples))) continue;
                engine.reset();
                // Lower playback only AFTER detection. The wake listener never
                // keeps the TV permanently quiet while waiting for a phrase.
                WakeAudioDucking.begin(this, prefs.wakeDuckingPercent());
                if (!bufferCommandUntilOverlayClaimsIt(chunk)) {
                    WakeAudioDucking.end(this);
                    sleep(2000);
                    continue;
                }
                long deadline = android.os.SystemClock.elapsedRealtime() + 5000L;
                while (running && !VoiceOverlayService.isOpen()
                        && android.os.SystemClock.elapsedRealtime() < deadline) {
                    sleep(100);
                }
                while (running && VoiceOverlayService.isOpen()
                        && prefs.wakeWord()) {
                    sleep(200);
                }
            }
        } catch (Throwable ignored) {
            notifyFailure("Wake word stopped. Turn it on again from Settings.");
            prefs.setWakeWord(false);
        } finally {
            running = false;
            releaseRecorder();
            MicLease.release(MicLease.WAKE);
            if (engine != null) engine.close();
            WakeAssistConnection.stop();
            if (!VoiceOverlayService.isOpen()) WakeAudioDucking.end(this);
            stopForeground(STOP_FOREGROUND_REMOVE);
            if (failureNotice != null) publishFailure(failureNotice);
            stopSelf();
        }
    }

    /**
     * Keep the same recorder after Okay Nabu until Assist claims the audio,
     * or until {@link WakeCommandLead#MAX_MS}. The recorder is released
     * before this returns, so Assist can open its own. Audio from before
     * detection is not kept.
     */
    private boolean bufferCommandUntilOverlayClaimsIt(short[] chunk) {
        WakeCommandLead.begin();
        boolean opened = openOverlay();
        AudioRecord currentRecorder;
        synchronized (micLock) {
            currentRecorder = recorder;
        }
        long started = android.os.SystemClock.elapsedRealtime();
        boolean overlaySeen = VoiceOverlayService.isOpen();
        try {
            while (running && opened && currentRecorder != null
                    && !WakeCommandLead.stopRequested()) {
                long elapsed = android.os.SystemClock.elapsedRealtime() - started;
                if (elapsed >= WakeCommandLead.MAX_MS) break;
                if (VoiceOverlayService.isOpen()) overlaySeen = true;
                int read;
                try {
                    read = currentRecorder.read(chunk, 0, CHUNK);
                } catch (RuntimeException stopped) {
                    break;
                }
                if (read > 0) WakeCommandLead.append(chunk, read);
                if (read < 0) break;
            }
        } finally {
            if (VoiceOverlayService.isOpen()) overlaySeen = true;
            releaseRecorder();
            MicLease.release(MicLease.WAKE);
            // Assist claims this buffer only after the overlay is actually up.
            // If the overlay never appeared, drop the audio. Do not upload it later.
            if (opened && overlaySeen) WakeCommandLead.finish();
            else {
                WakeCommandLead.discard();
                WakeAudioDucking.end(this);
            }
        }
        return opened;
    }

    private boolean openOverlay() {
        if (!new OverlayPreferences(this).wakeWord()) return false;
        if (VoiceOverlayService.isOpen()) return true;
        try {
            // The question is still being recorded. Assist claims that audio
            // before it opens a second recorder.
            startForegroundService(new Intent(this, VoiceOverlayService.class)
                    .setAction(WakeWordSession.ACTION_DETECTED));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean ensureRecorder() {
        synchronized (micLock) {
            if (!running) return false;
            if (recorder != null) return true;
            int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) return false;
            AudioRecord created;
            try {
                // getMinBufferSize reports BYTES. Keep about 500ms of PCM16 mono
                // available while CPU inference runs (16k samples/s * 2 bytes / 2).
                created = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minimum, SAMPLE_RATE));
            } catch (RuntimeException unsupported) {
                return false;
            }
            if (created.getState() != AudioRecord.STATE_INITIALIZED) {
                created.release();
                return false;
            }
            try {
                created.startRecording();
            } catch (RuntimeException error) {
                created.release();
                return false;
            }
            if (!running || created.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                created.release();
                return false;
            }
            if (MicrophoneRouting.unusableActualRoute(created,
                    new OverlayPreferences(this).allowUnverifiedMic())) {
                created.stop();
                created.release();
                return false;
            }
            if (!MicLease.wakeRecorderOpened()) {
                // A stale/cancelled wake open must never coexist with another
                // owner. Release before returning to the capture loop.
                try { created.stop(); } catch (RuntimeException ignored) { }
                created.release();
                return false;
            }
            recorder = created;
            return true;
        }
    }

    private void releaseRecorder() {
        AudioRecord held;
        synchronized (micLock) {
            held = recorder;
            recorder = null;
        }
        if (held == null) return;
        try {
            held.stop();
        } catch (RuntimeException ignored) {
        }
        held.release();
        MicLease.wakeRecorderReleased();
    }

    /** Unblock a read so the wake thread can release the recorder itself. */
    private void stopRecorder() {
        AudioRecord held;
        synchronized (micLock) {
            held = recorder;
        }
        if (held == null) return;
        try {
            held.stop();
        } catch (RuntimeException ignored) {
        }
    }

    private String readAsset(String path) throws java.io.IOException {
        try (InputStream in = getAssets().open(path)) {
            return new String(readBounded(in, 16_384), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private ByteBuffer loadModel(String path) throws java.io.IOException {
        try (InputStream in = getAssets().open(path)) {
            byte[] bytes = readBounded(in, 2_000_000);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.order(ByteOrder.nativeOrder());
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        }
    }

    private static byte[] readBounded(InputStream in, int cap) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > cap) throw new java.io.IOException("Wake word asset is too large");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private Notification buildNotice() {
        createChannel();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Kellie's Voice wake word")
                .setContentText("Listening for Okay Nabu. The microphone is in use.")
                .setOngoing(true)
                .build();
    }

    private void notifyFailure(String message) {
        // Store the reason until AFTER stopForeground removes NOTICE_ID.
        // Posting it under that same ID before cleanup would erase it.
        failureNotice = message;
    }

    private void publishFailure(String message) {
        createChannel();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.notify(FAILURE_NOTICE_ID, new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Kellie's Voice wake word stopped")
                .setContentText(message)
                .setAutoCancel(true)
                .setOngoing(false)
                .build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                "Kellie's Voice wake word", NotificationManager.IMPORTANCE_LOW));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override public void onDestroy() {
        running = false;
        WakeAssistConnection.stop();
        if (!VoiceOverlayService.isOpen()) WakeAudioDucking.end(this);
        if (current == this) current = null;
        releaseRecorder();
        MicLease.release(MicLease.WAKE);
        super.onDestroy();
    }
}
