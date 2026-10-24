package com.example.tvvoiceprobe;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Separate timer window and bounded callbacks. Never opens a microphone. */
public final class TimerOverlayController {
    private final Context context;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TimerBook timers = new TimerBook();
    private final AudioManager audio;
    private AudioFocusRequest focus;
    private final TimerAlertSession alerts;
    private boolean controlsReady;
    private LinearLayout panel;
    private MediaPlayer player;
    private TextToSpeech speech;
    private boolean speechReady;
    private boolean closed;
    private TimerBook.Entry alerting;
    private Runnable speechFallback;
    private String expectedSpeech;
    private int utterance;
    private int speakingGeneration;
    private String layout = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (timers.tick(SystemClock.elapsedRealtime())) save();
            render();
            chooseAlert();
            main.postDelayed(this, 1000);
        }
    };

    public TimerOverlayController(Context context, WindowManager windows) {
        this.context = context;
        this.windows = windows;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        alerts = new TimerAlertSession(new TimerAlertSession.Output() {
            @Override public boolean requestFocus(int token) { return requestAlertFocus(token); }
            @Override public void abandonFocus() {
                AudioFocusRequest previous = focus;
                focus = null;
                if (audio != null && previous != null) audio.abandonAudioFocusRequest(previous);
            }
            @Override public void stopPlayback() { stopAlertPlayback(); }
            @Override public void startPlayback(int token) {
                // Focus a real Dismiss control, not the surrounding LinearLayout.
                // The root is focusable only as a fallback for odd TV firmware.
                ensureDismissFocus(null);
                chime(token, 0);
            }
        });
        speech = new TextToSpeech(context, result -> main.post(() -> speechReady = result == TextToSpeech.SUCCESS));
        speech.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { main.post(() -> speechDone(id)); }
            @Override public void onError(String id) { main.post(() -> speechDone(id)); }
        });
        restore();
        main.post(tick);
    }

    public void command(JSONObject data) {
        if (closed || data.optLong("issued", 0) <= 0
                || !ForecastCard.fresh(data.optLong("issued", 0), System.currentTimeMillis() / 1000)) return;
        String name = data.optString("name", "Timer");
        String operation = data.optString("operation", "");
        if ("start".equals(operation)) {
            timers.start(name, data.optLong("ends_at_ms", 0), System.currentTimeMillis(),
                    SystemClock.elapsedRealtime(), data.optBoolean("speak_alert", false), data.optString("alert_phrase", ""));
        } else if ("cancel".equals(operation)) timers.cancel(name);
        else if ("dismiss".equals(operation)) timers.dismissRinging();
        else return;
        save();
        render();
        chooseAlert();
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private void render() {
        if (windows == null || !Settings.canDrawOverlays(context)) { removePanel(); return; }
        if (panel != null && !panel.isAttachedToWindow()) removePanel();
        boolean ringing = timers.ringing() != null;
        StringBuilder signature = new StringBuilder();
        for (TimerBook.Entry entry : timers.entries()) signature.append(entry.name).append(':').append(entry.finished).append('|');
        if (panel != null && signature.toString().equals(layout)) {
            int i = 0;
            for (TimerBook.Entry timer : timers.entries()) {
                ((TextView) panel.getChildAt(i++)).setText(timer.name + "   " + timer.remaining(SystemClock.elapsedRealtime())
                        + (timer.finished ? "  ·  Dismiss" : ""));
            }
            return;
        }
        layout = signature.toString();
        if (panel == null && !timers.entries().isEmpty()) {
            panel = new LinearLayout(context) {
                @Override public boolean dispatchKeyEvent(KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                            && timers.ringing() != null) {
                        dismissAllRinging(); return true;
                    }
                    if (TimerDismissKeys.shouldFallbackDismiss(event.getAction(), event.getKeyCode(),
                            timers.ringing() != null, findFocus() instanceof Button)) {
                        // On a TV whose window focused the root rather than the child,
                        // still let OK dismiss one timer instead of doing nothing.
                        TimerBook.Entry first = timers.ringing();
                        if (first != null) dismissTimer(first.name);
                        return true;
                    }
                    return super.dispatchKeyEvent(event);
                }

                @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
                    super.onWindowFocusChanged(hasWindowFocus);
                    // Android may grant window focus only after FLAG_NOT_FOCUSABLE
                    // is removed. Put it on the real button when that happens.
                    if (hasWindowFocus && timers.ringing() != null)
                        post(() -> ensureDismissFocus(null));
                }
            };
            panel.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(android.view.View view) { }
                @Override public void onViewDetachedFromWindow(android.view.View view) {
                    controlsReady = false;
                    alerts.update(timers.ringing(), false, SystemClock.elapsedRealtime());
                }
            });
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(14), dp(8), dp(14), dp(8));
            GradientDrawable surface = new GradientDrawable();
            surface.setColor(0xF510182B);
            surface.setCornerRadius(dp(20));
            surface.setStroke(dp(1), 0x887DAAFF);
            panel.setBackground(surface);
            panel.setFocusableInTouchMode(true);
            panel.setOnKeyListener((view, code, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_BACK && timers.ringing() != null) {
                    dismissAllRinging(); return true;
                }
                return false;
            });
            try { windows.addView(panel, params(ringing)); }
            catch (RuntimeException rejected) { removePanel(); return; }
        }
        if (panel == null) return;
        if (timers.entries().isEmpty()) { removePanel(); return; }
        // Retain the user's selected ringing timer during a rebuild.
        View previouslyFocused = panel.findFocus();
        String preferredDismiss = previouslyFocused instanceof Button
                && previouslyFocused.getTag() instanceof String
                ? (String) previouslyFocused.getTag() : null;
        panel.removeAllViews();
        for (TimerBook.Entry timer : timers.entries()) {
            String text = timer.name + "   " + timer.remaining(SystemClock.elapsedRealtime());
            if (timer.finished) {
                Button dismiss = new Button(context);
                dismiss.setText(text + "  ·  Dismiss");
                dismiss.setTextColor(0xFFFFFFFF);
                dismiss.setFocusable(true);
                dismiss.setFocusableInTouchMode(true);
                dismiss.setTag(TimerBook.key(timer.name));
                dismiss.setOnClickListener(view -> dismissTimer(timer.name));
                panel.addView(dismiss);
            } else {
                TextView pill = new TextView(context);
                pill.setText(text); pill.setTextSize(18); pill.setTextColor(0xFFE8EEFF);
                pill.setPadding(dp(8), dp(4), dp(8), dp(4));
                panel.addView(pill);
            }
        }
        try {
            windows.updateViewLayout(panel, params(ringing));
            controlsReady = ringing;
            if (ringing) ensureDismissFocus(preferredDismiss);
        } catch (RuntimeException rejected) { removePanel(); }
    }

    private void dismissTimer(String name) {
        if (!timers.cancel(name)) return;
        save();
        render();
        chooseAlert();
    }

    private void dismissAllRinging() {
        timers.dismissRinging();
        save();
        render();
        chooseAlert();
    }

    /** Choose a real D-pad clickable button rather than the focusable panel. */
    private Button dismissButton(String preferredName) {
        if (panel == null) return null;
        Button first = null;
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (!(child instanceof Button)) continue;
            Button button = (Button) child;
            if (first == null) first = button;
            if (preferredName != null && preferredName.equals(button.getTag())) return button;
        }
        return first;
    }

    private void ensureDismissFocus(String preferredName) {
        if (closed || panel == null || timers.ringing() == null) return;
        if (panel.findFocus() instanceof Button) return;
        Button target = dismissButton(preferredName);
        if (target == null) return;
        target.requestFocus();
        // Once Android grants focus to the newly focusable overlay window, a
        // posted retry handles devices that reject the initial request.
        LinearLayout owner = panel;
        main.post(() -> {
            if (closed || owner != panel || timers.ringing() == null
                    || panel.findFocus() instanceof Button) return;
            Button ready = dismissButton(preferredName);
            if (ready != null) ready.requestFocus();
        });
    }

    private WindowManager.LayoutParams params(boolean ringing) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!ringing) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        int width = Math.min(dp(420), context.getResources().getDisplayMetrics().widthPixels - dp(40));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(24);
        params.setTitle("Kellie's Voice timers");
        return params;
    }

    private void chooseAlert() {
        alerting = timers.ringing();
        alerts.update(alerting, controlsAvailable(), SystemClock.elapsedRealtime());
    }

    private boolean controlsAvailable() {
        return !closed && controlsReady && Settings.canDrawOverlays(context)
                && panel != null && panel.isAttachedToWindow()
                && panel.getWindowVisibility() == android.view.View.VISIBLE;
    }

    private boolean requestAlertFocus(int token) {
        if (audio == null || !controlsAvailable()) return false;
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                .setOnAudioFocusChangeListener(change -> main.post(() -> {
                    if (closed) return;
                    if (!controlsAvailable()) {
                        alerts.update(timers.ringing(), false, SystemClock.elapsedRealtime());
                        return;
                    }
                    TimerAlertSession.Focus event;
                    if (change == AudioManager.AUDIOFOCUS_GAIN) event = TimerAlertSession.Focus.GAIN;
                    else if (change == AudioManager.AUDIOFOCUS_LOSS) event = TimerAlertSession.Focus.LOSS;
                    else if (change < 0) event = TimerAlertSession.Focus.TRANSIENT_LOSS;
                    else return;
                    alerts.focusChanged(token, event, SystemClock.elapsedRealtime());
                })).build();
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private boolean current(int generation) {
        if (!controlsAvailable()) {
            alerts.update(timers.ringing(), false, SystemClock.elapsedRealtime());
            return false;
        }
        return alerts.isPlaying(generation);
    }

    private void chime(int generation, int index) {
        if (!current(generation)) return;
        releasePlayer();
        try {
            player = MediaPlayer.create(context, R.raw.timer_chime,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(), 0);
            if (player == null) { later(generation, 1500, () -> chime(generation, 0)); return; }
            player.setOnCompletionListener(done -> {
                if (!current(generation)) return;
                releasePlayer();
                if (index == 0) later(generation, 180, () -> chime(generation, 1));
                else speakOrRepeat(generation);
            });
            player.setOnErrorListener((failed, what, extra) -> {
                if (!current(generation)) return true;
                releasePlayer(); later(generation, 1500, () -> chime(generation, 0)); return true;
            });
            player.start();
        } catch (RuntimeException failed) { releasePlayer(); later(generation, 1500, () -> chime(generation, 0)); }
    }

    private void speakOrRepeat(int generation) {
        if (!current(generation)) return;
        if (!alerting.speak || !speechReady) { later(generation, 500, () -> chime(generation, 0)); return; }
        speakingGeneration = generation;
        speechFallback = () -> {
            if (!current(generation)) return;
            expectedSpeech = null; speechFallback = null; speech.stop();
            later(generation, 500, () -> chime(generation, 0));
        };
        main.postDelayed(speechFallback, 15000);
        expectedSpeech = "timer-" + generation + "-" + (++utterance);
        int result = speech.speak(alerting.phrase, TextToSpeech.QUEUE_FLUSH, null, expectedSpeech);
        if (result == TextToSpeech.ERROR) speechDone(expectedSpeech);
    }

    private void speechDone(String id) {
        if (expectedSpeech == null || !expectedSpeech.equals(id) || speechFallback == null) return;
        int generation = speakingGeneration;
        if (!current(generation)) return;
        expectedSpeech = null;
        main.removeCallbacks(speechFallback); speechFallback = null;
        later(generation, 500, () -> chime(generation, 0));
    }

    private void later(int generation, int delay, Runnable action) {
        main.postDelayed(() -> { if (current(generation)) action.run(); }, delay);
    }

    private void stopAlertPlayback() {
        if (speechFallback != null) main.removeCallbacks(speechFallback);
        speechFallback = null;
        expectedSpeech = null;
        if (speech != null) speech.stop();
        releasePlayer();
    }

    private void releasePlayer() { if (player != null) { player.release(); player = null; } }
    private void removePanel() {
        controlsReady = false;
        alerts.update(timers.ringing(), false, SystemClock.elapsedRealtime());
        if (panel != null) { try { windows.removeView(panel); } catch (RuntimeException ignored) { } panel = null; }
    }

    private void save() {
        JSONArray state = new JSONArray();
        for (TimerBook.Entry timer : timers.entries()) {
            try { state.put(new JSONObject().put("name", timer.name).put("end", timer.endEpochMs)
                    .put("speak", timer.speak).put("phrase", timer.phrase)); } catch (Exception ignored) { }
        }
        context.getSharedPreferences("kellie_timers", 0).edit().putString("active", state.toString()).apply();
    }

    private void restore() {
        try {
            JSONArray state = new JSONArray(context.getSharedPreferences("kellie_timers", 0).getString("active", "[]"));
            for (int i = 0; i < state.length() && i < TimerBook.MAX_TIMERS; i++) {
                JSONObject row = state.getJSONObject(i);
                timers.start(row.optString("name"), row.optLong("end"), System.currentTimeMillis(),
                        SystemClock.elapsedRealtime(), row.optBoolean("speak"), row.optString("phrase"));
            }
        } catch (Exception ignored) { }
    }

    public void close(boolean forget) {
        closed = true;
        alerts.close();
        main.removeCallbacksAndMessages(null);
        if (speech != null) { speech.shutdown(); speech = null; }
        removePanel();
        if (forget) context.getSharedPreferences("kellie_timers", 0).edit().clear().apply();
    }
}
