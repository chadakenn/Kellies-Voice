package com.example.tvvoiceprobe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/**
 * Optional on-TV speech with transient Android audio focus.
 * Do not change permanent device volume. Old TTS callbacks must never
 * abandon audio focus acquired by a newer announcement.
 */
public final class AnnouncementSpeech {
    private final Context context;
    private final AudioManager audio;
    private final AnnouncementSpeechSession sessions = new AnnouncementSpeechSession();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable focusWatchdog;
    private TextToSpeech speaker;
    private AudioFocusRequest focusRequest;
    private boolean initialized;
    private boolean enabled = true;
    private String pendingText;
    private int pendingGeneration = -1;

    public AnnouncementSpeech(Context context) {
        this.context = context.getApplicationContext();
        this.audio = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) stop();
    }

    public synchronized void speak(String phrase) {
        if (!enabled || phrase == null || phrase.isEmpty()) return;
        int gen = sessions.begin();
        // Stop the old voice before acquiring focus for a new one.
        cancelFocusWatchdog();
        if (speaker != null) {
            try { speaker.stop(); } catch (RuntimeException ignored) { }
        }
        releaseFocus();
        pendingText = phrase;
        pendingGeneration = gen;
        if (speaker == null) {
            try {
                speaker = new TextToSpeech(context, this::onInitialized);
            } catch (RuntimeException ignored) {
                fail(gen);
            }
        } else if (initialized) {
            speakPending();
        }
        // When TTS is still initializing, only the newest pending text is spoken.
    }

    private synchronized void onInitialized(int status) {
        initialized = status == TextToSpeech.SUCCESS;
        if (!initialized) {
            sessions.cancel();
            pendingText = null;
            pendingGeneration = -1;
            cancelFocusWatchdog();
            releaseFocus();
            if (speaker != null) {
                try { speaker.shutdown(); } catch (RuntimeException ignored) { }
                speaker = null; // Allow a fresh TTS initialization on the next event.
            }
            return;
        }
        speakPending();
    }

    private void speakPending() {
        if (!initialized || speaker == null || pendingText == null
                || !sessions.isCurrent(pendingGeneration)) return;
        final int gen = pendingGeneration;
        final String phrase = pendingText;
        final String utteranceId = "kv-announce-" + gen;
        pendingText = null;
        pendingGeneration = -1;
        try {
            speaker.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { complete(gen, id); }
                @Override public void onError(String id) { complete(gen, id); }
                @Override public void onError(String id, int code) { complete(gen, id); }
                @Override public void onStop(String id, boolean interrupted) { complete(gen, id); }
            });
            acquireFocus(gen);
            scheduleFocusWatchdog(gen);
            // speak() may return ERROR without ever producing an onError callback.
            if (speaker.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                    != TextToSpeech.SUCCESS) {
                complete(gen, utteranceId);
            }
        } catch (RuntimeException ignored) {
            fail(gen);
        }
    }

    private synchronized void complete(int gen, String utteranceId) {
        if (!("kv-announce-" + gen).equals(utteranceId)) return;
        if (sessions.finish(gen)) { cancelFocusWatchdog(); releaseFocus(); }
    }

    private synchronized void fail(int gen) {
        if (sessions.finish(gen)) releaseFocus();
        if (pendingGeneration == gen) {
            pendingGeneration = -1;
            pendingText = null;
        }
    }

    private void scheduleFocusWatchdog(int gen) {
        cancelFocusWatchdog();
        focusWatchdog = () -> {
            synchronized (AnnouncementSpeech.this) {
                if (sessions.isCurrent(gen)) stop();
            }
        };
        // Engine callbacks are not guaranteed. A bounded timeout avoids permanent ducking.
        main.postDelayed(focusWatchdog, 45_000L);
    }

    private void cancelFocusWatchdog() {
        if (focusWatchdog != null) main.removeCallbacks(focusWatchdog);
        focusWatchdog = null;
    }

    private void acquireFocus(int gen) {
        if (audio == null) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            AudioFocusRequest request = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS
                                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            synchronized (AnnouncementSpeech.this) {
                                if (sessions.isCurrent(gen)) stop();
                            }
                        }
                    }).build();
            if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = request;
            }
            // A TV may refuse focus; speech still works without guaranteed ducking.
        } catch (RuntimeException ignored) {
            // The TV vendor might not implement audio focus.
        }
    }

    private void releaseFocus() {
        AudioFocusRequest previous = focusRequest;
        focusRequest = null;
        if (previous == null || audio == null) return;
        try { audio.abandonAudioFocusRequest(previous); }
        catch (RuntimeException ignored) { }
    }

    public synchronized void stop() {
        sessions.cancel();
        cancelFocusWatchdog();
        pendingText = null;
        pendingGeneration = -1;
        if (speaker != null) {
            try { speaker.stop(); } catch (RuntimeException ignored) { }
        }
        releaseFocus();
    }

    public synchronized void shutdown() {
        stop();
        if (speaker != null) {
            try { speaker.shutdown(); } catch (RuntimeException ignored) { }
            speaker = null;
        }
        initialized = false;
    }
}
