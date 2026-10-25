package com.example.tvvoiceprobe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optional spoken announcements with transient audio ducking.
 * Focus is always released on completion, error, interruption, or explicit stop.
 * Generations prevent stale callbacks from affecting newer speech.
 * Never permanently alters system volume.
 */
public final class AnnouncementSpeech {
    private final Context context;
    private final AudioManager audio;
    private TextToSpeech speaker;
    private AudioFocusRequest focusRequest;
    private final AtomicInteger generation = new AtomicInteger(0);
    private int activeGeneration = -1;
    private boolean enabled = true; // caller checks prefs; this is runtime gate

    public AnnouncementSpeech(Context context) {
        this.context = context.getApplicationContext();
        this.audio = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) stop();
    }

    public synchronized void speak(String phrase) {
        if (!enabled || phrase == null || phrase.isEmpty()) return;
        final int gen = generation.incrementAndGet();
        activeGeneration = gen;
        releaseFocus(); // drop any previous
        if (speaker == null) {
            speaker = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS && activeGeneration == gen) {
                    configureAndSpeak(phrase, gen);
                } else if (activeGeneration == gen) {
                    releaseFocus();
                }
            });
            return;
        }
        configureAndSpeak(phrase, gen);
    }

    private void configureAndSpeak(String phrase, int gen) {
        if (speaker == null || activeGeneration != gen) return;
        try {
            speaker.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) {
                    if (activeGeneration == gen) releaseFocus();
                }
                @Override public void onError(String utteranceId) {
                    if (activeGeneration == gen) releaseFocus();
                }
                @Override public void onStop(String utteranceId, boolean interrupted) {
                    if (activeGeneration == gen) releaseFocus();
                }
            });
            acquireFocus();
            // Optional volume scaling is not applied here; system TTS uses default stream.
            speaker.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "kv-announce-" + gen);
        } catch (RuntimeException ignored) {
            releaseFocus();
        }
    }

    private void acquireFocus() {
        if (audio == null) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(change -> {
                        // Transient loss during speech: stop and release to avoid stuck duck.
                        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            stop();
                        }
                    })
                    .build();
            if (audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = req;
            }
        } catch (RuntimeException ignored) {
            // Vendor may reject; speech still attempts without guaranteed duck.
        }
    }

    public synchronized void stop() {
        activeGeneration = -1;
        if (speaker != null) {
            try { speaker.stop(); } catch (RuntimeException ignored) { }
        }
        releaseFocus();
    }

    private void releaseFocus() {
        if (focusRequest == null || audio == null) return;
        try {
            audio.abandonAudioFocusRequest(focusRequest);
        } catch (RuntimeException ignored) { }
        focusRequest = null;
    }

    public synchronized void shutdown() {
        stop();
        if (speaker != null) {
            speaker.shutdown();
            speaker = null;
        }
    }
}
