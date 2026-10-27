package com.example.tvvoiceprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

/**
 * Best-effort local media ducking only after an opted-in wake phrase fires.
 *
 * This changes STREAM_MUSIC on devices with adjustable local volume, not an
 * individual YouTube/HDMI app. Android TV may use fixed/CEC routing and decline
 * to apply it. The setting does not help recognition BEFORE the wake phrase.
 */
final class WakeAudioDucking {
    private static final String PREFS = "kellie_wake_audio_restore";
    private static final String OLD = "old";
    private static final String DUCKED = "ducked";
    private static final String PENDING = "pending";
    private static boolean active;
    private static AudioManager audio;
    private static int previous = -1;
    private static int applied = -1;

    private WakeAudioDucking() { }

    static synchronized void recover(Context context) {
        if (active) return;
        SharedPreferences saved = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!saved.getBoolean(PENDING, false)) return;
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        try {
            if (manager != null && !manager.isVolumeFixed()
                    && manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            == saved.getInt(DUCKED, -1)) {
                manager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        saved.getInt(OLD, 0), 0);
            }
        } catch (RuntimeException ignored) {
            // A fixed-volume TV, missing permission, or provider audio policy
            // cannot prevent startup. Never force an unrelated volume change.
        } finally {
            saved.edit().clear().commit();
        }
    }

    static synchronized void begin(Context context, int configuredPercent) {
        if (active) return;
        recover(context);
        int percent = WakeDuckingLevels.normalize(configuredPercent);
        if (percent == 0) return;
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;
        try {
            if (manager.isVolumeFixed()) return;
            int original = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int target = WakeDuckingLevels.target(original, percent);
            if (target == original) return;

            SharedPreferences saved = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            // Persist first so we can undo after process death on next launch.
            if (!saved.edit().putInt(OLD, original).putInt(DUCKED, target)
                    .putBoolean(PENDING, true).commit()) return;
            try {
                manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                int now = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (now == original) {
                    // The TV ignored the request. Do not pretend it is ducked.
                    saved.edit().clear().commit();
                    return;
                }
                // A TV with coarse steps may land beside the requested level.
                // Remember the level it actually applied, or a later wake
                // ducks that already-lower volume again.
                if (now != target) {
                    saved.edit().putInt(OLD, original).putInt(DUCKED, now)
                            .putBoolean(PENDING, true).commit();
                }
                audio = manager;
                previous = original;
                applied = now;
                active = true;
            } catch (RuntimeException fail) {
                saved.edit().clear().commit();
            }
        } catch (RuntimeException ignored) {
            // Unsupported device; never block the wake detector/Assist pipeline.
        }
    }

    static synchronized void end(Context context) {
        if (!active) {
            recover(context);
            return;
        }
        boolean forget = false;
        try {
            // If the user changed volume during the command, respect it.
            if (audio != null && !audio.isVolumeFixed()
                    && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == applied) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, previous, 0);
                forget = audio.getStreamVolume(AudioManager.STREAM_MUSIC) == previous;
            } else {
                forget = true;
            }
        } catch (RuntimeException ignored) {
            // Do not crash speech/overlay cleanup on an OEM audio error.
            // Leave the recovery record so the next open can try again.
        } finally {
            active = false;
            audio = null;
            previous = -1;
            applied = -1;
            if (forget) {
                context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().clear().commit();
            }
        }
    }
}
