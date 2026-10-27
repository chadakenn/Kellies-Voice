package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Diagnostic only: key events, default-assistant eligibility, audio inputs, audio level. */
public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 41;
    private TextView output;
    private AppPalette palette;
    private ScrollView scroll;
    // Android TV requires explicit D-pad focus and a visibly selected action.
    private final List<Button> actions = new ArrayList<>();
    private int selectedAction = 0;
    private volatile boolean probing = false;
    // Remember which source was requested if the runtime permission dialog appears.
    private int pendingAudioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        palette = AppPalette.forSkin(new OverlayPreferences(this).skin());
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);
        layout.setBackgroundColor(palette.screen);
        TextView heading = new TextView(this);
        heading.setText("Kellie's Voice diagnostics  •  onn / Google TV");
        heading.setTextSize(23);
        heading.setTextColor(palette.text);
        layout.addView(heading);
        TextView help = new TextView(this);
        help.setText("Use remote UP / DOWN to choose a test, then press OK");
        help.setTextColor(palette.accent);
        help.setTextSize(17);
        layout.addView(help);
        addButton(layout, "List audio inputs", v -> listInputs());
        addButton(layout, "Check / request default assistant", v -> requestAssistant());
        addButton(layout, "Test MIC audio source (10 seconds)", v -> requestAudio(MediaRecorder.AudioSource.MIC));
        addButton(layout, "Test VOICE_RECOGNITION source (10 seconds)", v -> requestAudio(MediaRecorder.AudioSource.VOICE_RECOGNITION));
        addButton(layout, "Clear log", v -> output.setText(""));
        scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(palette.text);
        output.setTextSize(16);
        // A selectable text view can steal initial keyboard focus on TV.
        output.setTextIsSelectable(false);
        output.setFocusable(false);
        scroll.setFocusable(false);
        scroll.addView(output);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        // Preselect the first action: a remote user should never need a mouse.
        if (!actions.isEmpty()) {
            actions.get(0).post(() -> selectAction(0));
        }
        log("First test MIC and VOICE_RECOGNITION while speaking near the TV box.");
        log("Then repeat while holding the remote microphone button and speaking into the remote.");
        log("Remote voice key may launch Google's assistant instead of appearing in this app.");
        log("These tests measure signal levels only; no audio is saved or transmitted.");
        handleAssistIntent(getIntent());
        listInputs();
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener click) {
        Button b = new Button(this);
        final int index = actions.size();
        b.setText(label);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setTextColor(palette.text);
        b.setBackgroundTintList(ColorStateList.valueOf(palette.button));
        b.setOnClickListener(click);
        b.setOnFocusChangeListener((view, focused) -> {
            if (focused) selectedAction = index;
            b.setBackgroundTintList(ColorStateList.valueOf(
                    focused ? palette.focus : palette.button));
        });
        actions.add(b);
        parent.addView(b);
    }

    private void selectAction(int index) {
        if (actions.isEmpty()) return;
        selectedAction = Math.max(0, Math.min(index, actions.size() - 1));
        actions.get(selectedAction).requestFocus();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !actions.isEmpty()) {
            boolean anyActionFocused = false;
            for (Button b : actions) {
                if (b.hasFocus()) {
                    anyActionFocused = true;
                    break;
                }
            }
            if (!anyActionFocused) actions.get(selectedAction).post(() -> selectAction(selectedAction));
        }
    }

    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAssistIntent(intent);
    }

    private void handleAssistIntent(Intent intent) {
        if (intent != null && Intent.ACTION_ASSIST.equals(intent.getAction())) {
            log("ASSIST intent received! The OS routed assistant invocation to this app.");
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        final int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            log("Key: " + KeyEvent.keyCodeToString(key) +
                    " (" + key + ") inputDevice=" + event.getDeviceId());
        }

        // Explicit fallback even if Google TV does not assign focus normally.
        // Consume both DOWN and UP so the focused button never double-clicks.
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    selectAction(selectedAction + 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    selectAction(selectedAction - 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                        && !actions.isEmpty()) {
                    actions.get(selectedAction).performClick();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void requestAssistant() {
        if (Build.VERSION.SDK_INT < 29) {
            log("Assistant RoleManager needs Android 10+.");
            return;
        }
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            log("Default-assistant role is NOT available on this firmware.");
            return;
        }
        if (rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            log("This app already holds the assistant role.");
        } else {
            log("Requesting assistant role. Android must approve; Google TV may block or ignore it.");
            try { startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), 42); }
            catch (Exception e) { log("Role request failed: " + e.getMessage()); }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 42) log("Assistant role request result = " + resultCode + " (-1 = accepted)");
    }

    private void listInputs() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) { log("AudioManager unavailable"); return; }
        AudioDeviceInfo[] inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS);
        log("Audio input devices = " + inputs.length);
        for (AudioDeviceInfo d : inputs) {
            log("  id=" + d.getId() + " type=" + d.getType() + " name=" + d.getProductName() +
                " sampleRates=" + Arrays.toString(d.getSampleRates()));
        }
        log("A Bluetooth remote may use vendor GATT instead of appearing in this list.");
    }

    private void requestAudio(int source) {
        if (probing) { log("Mic test already running"); return; }
        pendingAudioSource = source;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
        } else { micTest(source); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) micTest(pendingAudioSource);
        else if (requestCode == REQUEST_AUDIO) log("Microphone permission denied.");
    }

    private String sourceName(int source) {
        return source == MediaRecorder.AudioSource.MIC ? "MIC" : "VOICE_RECOGNITION";
    }

    private String deviceName(AudioDeviceInfo device) {
        if (device == null) return "unknown / unavailable";
        return device.getProductName() + " (id " + device.getId() + ", type " + device.getType() + ")";
    }

    private void micTest(int source) {
        probing = true;
        new Thread(() -> {
            AudioRecord rec = null;
            try {
                int rate = 16000;
                int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) {
                    rate = 48000;
                    min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                }
                if (min <= 0) throw new IllegalStateException("No usable mono PCM capture format");
                rec = new AudioRecord(source, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(min * 2, 8192));
                if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord initialization failed");
                rec.startRecording();
                if (rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    throw new IllegalStateException("Capture did not start recording");
                }
                log("Testing " + sourceName(source) + " (" + rate + " Hz) for 10 sec.");
                log("Speak by the box first, then hold remote mic key and speak into remote.");
                String previousRoute = deviceName(rec.getRoutedDevice());
                log("Actual capture route: " + previousRoute);
                short[] samples = new short[2048];
                long until = SystemClock.elapsedRealtime() + 10000;
                long lastLog = 0;
                while (SystemClock.elapsedRealtime() < until) {
                    int read = rec.read(samples, 0, samples.length);
                    if (read <= 0) { log("Capture read error: " + read); break; }
                    long squareSum = 0;
                    int peak = 0;
                    for (int i = 0; i < read; i++) {
                        int x = samples[i];
                        peak = Math.max(peak, Math.abs(x));
                        squareSum += (long)x * x;
                    }
                    if (SystemClock.elapsedRealtime() - lastLog > 400) {
                        lastLog = SystemClock.elapsedRealtime();
                        double rms = Math.sqrt(squareSum / (double)read) / 32768;
                        String nowRoute = deviceName(rec.getRoutedDevice());
                        if (!nowRoute.equals(previousRoute)) {
                            log("Capture route changed: " + nowRoute);
                            previousRoute = nowRoute;
                        }
                        log(String.format(Locale.US, "%s peak=%d /32768  RMS=%.3f",
                                sourceName(source), peak, rms));
                    }
                }
            } catch (Exception e) { log("Mic test failed: " + e); }
            finally {
                if (rec != null) { try { rec.stop(); } catch (Exception ignored) {} rec.release(); }
                probing = false;
                log("Mic test ended. Audio was NOT stored or sent anywhere.");
            }
        }, "kellies-voice-diagnostics").start();
    }

    private void log(String message) {
        runOnUiThread(() -> {
            if (output == null) return;
            String old = output.getText().toString();
            if (old.length() > 10000) old = old.substring(old.length() - 7000);
            output.setText(old + "\n" + message);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }
}
