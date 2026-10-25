package com.example.tvvoiceprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Foreground push-to-talk client for Home Assistant Assist's authenticated WebSocket API.
 * Streams 16 kHz/mono/PCM16 only while actively listening, with the server's handler byte.
 * Not an ESPHome API client and not yet an Assist Satellite / background TV service.
 */
public final class HaAssistClient {
    public interface Events {
        void status(String message);
        void transcript(String text);
        void answer(String text);
        void ttsUrl(String url);
        // Optional typed lifecycle callbacks; existing full-screen client
        // continues to work without implementing these methods.
        default void ready() { }
        default void listening() { }
        default void processing() { }
        default void haLayout(String setting) { }
        default void localSilence() { }
        default void runEnded() { }
        /** Unexpected socket loss; in-progress Assist run has been reset. */
        default void connectionLost() { }
    }

    private final Context context;
    private final Events events;
    private final SpeechFocus listeningFocus;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build();
    private volatile WebSocket socket;
    private volatile boolean authenticated;
    private volatile int currentRunId = -1;
    private volatile int handlerId = -1;
    private volatile boolean endRequested;
    private volatile boolean captureStarted;
    private volatile boolean autoEndSpeech;
    private volatile boolean microphoneMissing;
    private volatile String roomNoise = OverlayPreferences.NOISE_NORMAL;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private volatile String conversationId;
    private volatile int commandId = 1;
    private volatile boolean layoutSync;
    private volatile boolean layoutSubscribed;
    private volatile int layoutStatesId = -1;
    private final Map<String, String> layoutEntities = new HashMap<>();
    private volatile boolean disposed = true;

    // Keep one authenticated WebSocket alive only for the lifetime of the
    // explicit activity/overlay. Android can suspend or kill the app, so this
    // is self-healing, not an always-running background assistant service.
    private String savedBaseUrl;
    private String savedToken;
    private boolean reconnectEnabled;
    private boolean reconnectScheduled;
    private boolean connecting;
    private int failures;
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final Runnable reconnectTask = () -> {
        synchronized (HaAssistClient.this) {
            reconnectScheduled = false;
            if (reconnectEnabled && !disposed && !authenticated && !connecting) {
                openConnection();
            }
        }
    };

    public HaAssistClient(Context context, Events events) {
        this.context = context.getApplicationContext();
        this.events = events;
        this.listeningFocus = new SpeechFocus(this.context);
    }

    public boolean isConnected() { return authenticated; }

    /** Whether reconnection is enabled; rejected credentials must not be retried. */
    public synchronized boolean canReconnect() {
        return !disposed && reconnectEnabled && savedToken != null;
    }

    /** Explicit start/reconfigure. Tokens never enter logs or retry messages. */
    public synchronized void connect(String baseUrl, String token) {
        disconnect();
        if (baseUrl == null || token == null || token.trim().isEmpty()) {
            events.status("Missing Home Assistant address or access token.");
            return;
        }
        try {
            okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(baseUrl);
            if (parsed == null ||
                    (!"https".equals(parsed.scheme()) && !"http".equals(parsed.scheme()))) {
                throw new IllegalArgumentException("Use a full Home Assistant http:// or https:// address.");
            }
            // Reject a URL with credentials in it. Use only the saved HA access token.
            if (!parsed.username().isEmpty() || !parsed.password().isEmpty()) {
                throw new IllegalArgumentException("Do not put credentials in the Home Assistant URL.");
            }
            savedBaseUrl = baseUrl;
            savedToken = token;
            disposed = false;
            reconnectEnabled = true;
            failures = 0;
            openConnection();
        } catch (Exception error) {
            events.status("Invalid Home Assistant address. Check settings.");
        }
    }

    /**
     * When the app returns to the foreground, reconnect if Android interrupted
     * its socket. Never start a microphone or a voice request here.
     */
    public synchronized void ensureConnected() {
        if (disposed || !reconnectEnabled || authenticated || connecting
                || savedToken == null) return;
        // Foreground user activity should reconnect immediately instead of
        // waiting out a previous 30-second background backoff timer.
        if (reconnectScheduled) {
            reconnectHandler.removeCallbacks(reconnectTask);
            reconnectScheduled = false;
        }
        openConnection();
    }

    private synchronized void openConnection() {
        if (disposed || !reconnectEnabled || connecting || authenticated
                || savedBaseUrl == null || savedToken == null) return;
        try {
            okhttp3.HttpUrl base = okhttp3.HttpUrl.parse(savedBaseUrl);
            if (base == null) throw new IllegalStateException("Missing HA URL");
            okhttp3.HttpUrl url = base.newBuilder()
                    .addPathSegments("api/websocket").build();
            final String authToken = savedToken;
            connecting = true;
            events.status(failures == 0 ? "Connecting to Home Assistant..."
                    : "Reconnecting to Home Assistant...");
            socket = http.newWebSocket(new Request.Builder().url(url).build(),
                    new WebSocketListener() {
                @Override public void onOpen(WebSocket ws, Response response) {
                    if (isCurrent(ws)) {
                        events.status("Connected to server, waiting for authentication...");
                    }
                }

                @Override public void onMessage(WebSocket ws, String message) {
                    if (!isCurrent(ws)) return;
                    try { receive(ws, new JSONObject(message), authToken); }
                    catch (Exception e) {
                        if (isCurrent(ws)) events.status("Unexpected Home Assistant response.");
                    }
                }

                @Override public void onFailure(WebSocket ws, Throwable error,
                        Response response) {
                    connectionLost(ws);
                }

                @Override public void onClosed(WebSocket ws, int code, String reason) {
                    connectionLost(ws);
                }
            });
        } catch (Exception error) {
            connecting = false;
            scheduleReconnect();
        }
    }

    private synchronized boolean isCurrent(WebSocket ws) {
        return !disposed && reconnectEnabled && ws == socket;
    }

    private void connectionLost(WebSocket ws) {
        synchronized (this) {
            // The old socket can close AFTER a new one authenticated. Never
            // allow its callback to mark the replacement disconnected.
            if (ws != socket || disposed) return;
            socket = null;
            authenticated = false;
            connecting = false;
            layoutSubscribed = false;
            layoutStatesId = -1;
            layoutEntities.clear();
            stopForRunEnd();
            if (reconnectEnabled) scheduleReconnect();
        }
        events.connectionLost();
    }

    private synchronized void scheduleReconnect() {
        if (disposed || !reconnectEnabled || reconnectScheduled) return;
        // 2, 4, 8, 16, 30 seconds with a cap; prevents rapid connection loops
        // when HA restarts or the TV's Wi-Fi is down.
        int attempt = Math.min(failures++, 4);
        long delay = Math.min(30000L, 2000L * (1L << attempt));
        reconnectScheduled = true;
        events.status("Home Assistant disconnected. Retrying automatically in "
                + (delay / 1000L) + " seconds...");
        reconnectHandler.postDelayed(reconnectTask, delay);
    }

    private void receive(WebSocket ws, JSONObject msg, String token) throws Exception {
        if (ws != socket || disposed) return;
        String type = msg.optString("type");
        if ("auth_required".equals(type)) {
            JSONObject auth = new JSONObject();
            auth.put("type", "auth");
            auth.put("access_token", token);
            ws.send(auth.toString());
            return;
        }
        if ("auth_ok".equals(type)) {
            synchronized (this) {
                if (!isCurrent(ws)) return;
                authenticated = true;
                connecting = false;
                failures = 0;
                reconnectHandler.removeCallbacks(reconnectTask);
                reconnectScheduled = false;
            }
            events.status("Connected and authenticated. Ready.");
            events.ready();
            requestLayout();
            return;
        }
        if ("auth_invalid".equals(type)) {
            synchronized (this) {
                if (!isCurrent(ws)) return;
                authenticated = false;
                connecting = false;
                reconnectEnabled = false; // Invalid credentials cannot heal by retrying.
                recording.set(false);
                stopForRunEnd();
                reconnectHandler.removeCallbacks(reconnectTask);
                reconnectScheduled = false;
            }
            events.status("Authentication rejected. Check the long-lived access token.");
            ws.close(1000, "invalid auth");
            return;
        }
        if ("result".equals(type)) {
            int id = msg.optInt("id", -1);
            if (id == layoutStatesId) {
                layoutStatesId = -1;
                if (msg.optBoolean("success", false)) {
                    JSONArray states = msg.optJSONArray("result");
                    if (states != null) ingestStates(states);
                }
                return;
            }
            if (id == currentRunId && !msg.optBoolean("success", false)) {
                JSONObject error = msg.optJSONObject("error");
                events.status(AssistReadinessPolicy.assistError(
                        error == null ? "" : error.optString("code", ""),
                        error == null ? "" : error.optString("message", "")));
                stopForRunEnd();
            }
            return;
        }
        if ("event".equals(type)) {
            JSONObject event = msg.optJSONObject("event");
            if (event != null && "state_changed".equals(event.optString("event_type"))) {
                if (layoutSync) ingestState(event.optJSONObject("data"));
                return;
            }
        }
        if (!"event".equals(type) || msg.optInt("id", -1) != currentRunId) return;
        JSONObject event = msg.optJSONObject("event");
        if (event == null) return;
        JSONObject data = event.optJSONObject("data");
        if (data == null) data = new JSONObject();
        String eventType = event.optString("type");
        // Once the recorder proves there is no usable route, do not replace
        // the actionable microphone message with STT no-text errors.
        if (microphoneMissing && !"error".equals(eventType)
                && !"run-end".equals(eventType)) return;
        switch (eventType) {
            case "run-start":
                JSONObject runner = data.optJSONObject("runner_data");
                int id = runner == null ? -1 : runner.optInt("stt_binary_handler_id", -1);
                if (id < 0 || id > 255) {
                    events.status("Assist did not provide a valid audio handler ID");
                    stopForRunEnd();
                    break;
                }
                handlerId = id;
                events.status("Audio handler ready. Preparing microphone...");
                break;
            case "stt-start":
                if (endRequested) sendEndOfAudio(currentRunId);
                else beginCapture(currentRunId);
                break;
            case "stt-vad-end":
                endRequested = true;
                recording.set(false);
                events.status("Speech detected. Processing...");
                events.processing();
                break;
            case "stt-end":
                endRequested = true;
                recording.set(false);
                JSONObject stt = data.optJSONObject("stt_output");
                if (stt != null) events.transcript(stt.optString("text", ""));
                events.status("Thinking...");
                events.processing();
                break;
            case "intent-start":
                events.status("Home Assistant is responding...");
                break;
            case "intent-end":
                JSONObject out = data.optJSONObject("intent_output");
                if (out != null) {
                    String nextConversationId = out.optString("conversation_id", "");
                    if (!nextConversationId.isEmpty()) conversationId = nextConversationId;
                    JSONObject response = out.optJSONObject("response");
                    if (response != null) {
                        JSONObject speech = response.optJSONObject("speech");
                        JSONObject plain = speech == null ? null : speech.optJSONObject("plain");
                        if (plain != null) events.answer(plain.optString("speech", ""));
                    }
                }
                break;
            case "tts-start":
                events.status("Generating spoken response...");
                break;
            case "tts-end":
                // HA supplies the playback URL INSIDE tts_output, not at the
                // top level of the event. Earlier builds silently missed TTS.
                JSONObject tts = data.optJSONObject("tts_output");
                String audio = tts == null ? data.optString("url", "")
                        : tts.optString("url", "");
                if (!audio.isEmpty()) events.ttsUrl(audio);
                else events.status("Home Assistant didn't return a TTS audio URL.");
                break;
            case "error":
                if (microphoneMissing) {
                    stopForRunEnd();
                    events.processing();
                    events.status(MicRoutePolicy.NO_MIC_MESSAGE);
                    events.runEnded();
                    break;
                }
                events.status(AssistReadinessPolicy.assistError(
                        data.optString("code", ""), data.optString("message", "")));
                stopForRunEnd();
                break;
            case "run-end":
                boolean routeMissing = microphoneMissing;
                stopForRunEnd();
                events.status(routeMissing ? MicRoutePolicy.NO_MIC_MESSAGE
                        : "Ready for another question.");
                events.runEnded();
                break;
            default:
                break;
        }
    }

    public void setRoomNoise(String mode) {
        roomNoise = OverlayPreferences.NOISE_QUIET.equals(mode)
                || OverlayPreferences.NOISE_TV.equals(mode)
                ? mode : OverlayPreferences.NOISE_NORMAL;
    }

    public synchronized boolean talk() {
        return talk(false);
    }

    /**
     * Auto mode begins listening on command and uses conservative local
     * silence detection to finish after an utterance. Manual STOP remains
     * available if TV playback sound confuses the detector.
     */
    public synchronized boolean talk(boolean autoStopOnSilence) {
        if (!authenticated || socket == null) {
            events.status(canReconnect()
                    ? AssistReadinessPolicy.waitingForConnection()
                    : "Home Assistant is not connected. Check login in Settings.");
            return false;
        }
        if (currentRunId != -1) {
            events.status("Finish the current conversation first.");
            return false;
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            events.status("Microphone permission is required.");
            return false;
        }
        String routeIssue = MicrophoneRouting.preflight(context,
                new OverlayPreferences(context).allowUnverifiedMic());
        if (routeIssue != null) {
            events.status(routeIssue);
            return false; // No Assist run or misleading LISTENING state.
        }
        try {
            JSONObject request = new JSONObject();
            int id = commandId++;
            request.put("id", id);
            request.put("type", "assist_pipeline/run");
            request.put("start_stage", "stt");
            request.put("end_stage", "tts");
            JSONObject input = new JSONObject();
            input.put("sample_rate", 16000);
            request.put("input", input);
            if (conversationId != null) request.put("conversation_id", conversationId);
            // Only use the HA registry identity when Home Assistant layout sync is enabled.
            if (layoutSync) {
                String registryDeviceId = new OverlayPreferences(context).registryDeviceId();
                if (!registryDeviceId.isEmpty()) request.put("device_id", registryDeviceId);
            }
            handlerId = -1;
            endRequested = false;
            captureStarted = false;
            autoEndSpeech = autoStopOnSilence;
            ended.set(false);
            microphoneMissing = false;
            currentRunId = id;
            // Duck YouTube before the Assist server replies and before opening
            // the microphone. The automatic fade needs a head start.
            listeningFocus.acquire();
            if (!socket.send(request.toString())) {
                currentRunId = -1;
                listeningFocus.release();
                events.status("Couldn't send Assist request.");
                return false;
            }
            events.status("Preparing to listen...");
            return true;
        } catch (Exception e) {
            currentRunId = -1;
            listeningFocus.release();
            events.status("Couldn't start Assist pipeline.");
            return false;
        }
    }

    public void finishTalking() {
        if (currentRunId < 0) {
            events.status("Press TALK first.");
            return;
        }
        endRequested = true;
        if (!captureStarted && handlerId >= 0) {
            sendEndOfAudio(currentRunId);
        }
        recording.set(false);
        events.status("Finishing audio. Waiting for Home Assistant...");
    }

    private void beginCapture(int runId) {
        synchronized (this) {
            if (captureStarted || runId != currentRunId || handlerId < 0 || disposed) return;
            captureStarted = true;
            recording.set(true);
        }
        new Thread(() -> {
            AudioRecord recorder = null;
            AcousticEchoCanceler echo = null;
            NoiseSuppressor noise = null;
            try {
                int minimum = AudioRecord.getMinBufferSize(16000,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minimum <= 0) throw new IllegalStateException("16kHz microphone audio is unsupported");
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minimum * 2, 6400));
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
                    throw new IllegalStateException("AudioRecord initialization failed");
                // Android TV firmware may or may not expose hardware echo
                // cancellation / noise suppression. Never let optional
                // effects break an otherwise working microphone.
                int session = recorder.getAudioSessionId();
                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        echo = AcousticEchoCanceler.create(session);
                        if (echo != null) echo.setEnabled(true);
                    }
                } catch (RuntimeException ignored) {
                    if (echo != null) { echo.release(); echo = null; }
                }
                try {
                    if (NoiseSuppressor.isAvailable()) {
                        noise = NoiseSuppressor.create(session);
                        if (noise != null) noise.setEnabled(true);
                    }
                } catch (RuntimeException ignored) {
                    if (noise != null) { noise.release(); noise = null; }
                }
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                    throw new IllegalStateException("Microphone recording was not started");
                if (MicrophoneRouting.unusableActualRoute(recorder,
                        new OverlayPreferences(context).allowUnverifiedMic())) {
                    microphoneMissing = true;
                    events.status(MicRoutePolicy.NO_MIC_MESSAGE);
                    events.processing();
                    return; // finally closes capture; no bogus PCM is sent.
                }
                events.status("LISTENING. Speak toward your microphone; select STOP when done.");
                events.listening();
                final String noiseMode = roomNoise;
                byte[] pcm = new byte[3200]; // 100 ms at 16kHz, mono, 16-bit
                long observedMs = 0L;
                long voiceMs = 0L;
                long quietMs = 0L;
                boolean heardUtterance = false;
                double backgroundRms = 0.002;
                while (recording.get() && runId == currentRunId) {
                    int read = recorder.read(pcm, 0, pcm.length);
                    if (!recording.get() || runId != currentRunId) break;
                    if (read <= 0) throw new IllegalStateException("Microphone read failed (" + read + ")");
                    if (read % 2 != 0) read--; // PCM16 samples must be complete
                    if (read == 0) continue;

                    if (autoEndSpeech) {
                        long chunkMs = Math.max(1, read / 32);
                        observedMs += chunkMs;
                        double power = 0d;
                        for (int sample = 0; sample + 1 < read; sample += 2) {
                            short value = (short) ((pcm[sample] & 255) |
                                    (pcm[sample + 1] << 8));
                            double normalized = value / 32768.0;
                            power += normalized * normalized;
                        }
                        double rms = Math.sqrt(power / Math.max(1, read / 2));
                        boolean television = OverlayPreferences.NOISE_TV.equals(noiseMode);
                        boolean quietRoom = OverlayPreferences.NOISE_QUIET.equals(noiseMode);
                        // TV mode learns its background for the first 600 ms;
                        // Normal mode retains existing thresholds and behavior.
                        if (television && observedMs < 600) {
                            backgroundRms = backgroundRms * 0.8 + rms * 0.2;
                        } else if (!heardUtterance && rms < backgroundRms * 2.2) {
                            backgroundRms = backgroundRms * 0.96 + rms * 0.04;
                        }
                        if (!(television && observedMs < 600)) {
                            double floor = quietRoom ? 0.003 : television ? 0.01 : 0.0045;
                            double times = quietRoom ? 1.9 : television ? 3.0 : 2.4;
                            long needVoice = quietRoom ? 140 : television ? 280 : 180;
                            long needQuiet = quietRoom ? 1100 : television ? 2000 : 1550;
                            boolean voice = rms > Math.max(floor, backgroundRms * times);
                            if (!heardUtterance) {
                                voiceMs = voice ? voiceMs + chunkMs : Math.max(0, voiceMs - chunkMs);
                                if (voiceMs >= needVoice) heardUtterance = true;
                            } else {
                                quietMs = voice ? 0 : quietMs + chunkMs;
                            }
                            if (heardUtterance && quietMs >= needQuiet && observedMs >= 1100) {
                                events.status("Voice pause detected. Processing your question...");
                                events.localSilence();
                                endRequested = true;
                                recording.set(false);
                            }
                        }
                    }
                    WebSocket ws = socket;
                    int handler = handlerId;
                    if (ws == null || handler < 0) break;
                    byte[] packet = new byte[read + 1];
                    packet[0] = (byte) handler;
                    System.arraycopy(pcm, 0, packet, 1, read);
                    if (!ws.send(ByteString.of(packet))) throw new IllegalStateException("Audio socket closed");
                }
            } catch (Exception e) {
                events.status("Microphone streaming failed: " + e.getMessage());
            } finally {
                recording.set(false);
                if (recorder != null) {
                    try { recorder.stop(); } catch (Exception ignored) { }
                }
                if (echo != null) echo.release();
                if (noise != null) noise.release();
                if (recorder != null) recorder.release();
                // A stale capture thread must never release a newer run's focus.
                if (runId == currentRunId) listeningFocus.release();
                sendEndOfAudio(runId);
            }
        }, "ha-assist-audio").start();
    }

    private void sendEndOfAudio(int runId) {
        if (runId != currentRunId || handlerId < 0 || !ended.compareAndSet(false, true)) return;
        WebSocket ws = socket;
        if (ws != null) ws.send(ByteString.of(new byte[] { (byte)handlerId }));
    }

    private void stopForRunEnd() {
        recording.set(false);
        listeningFocus.release();
        handlerId = -1;
        currentRunId = -1;
        captureStarted = false;
        endRequested = false;
    }

    public synchronized void setLayoutSync(boolean enabled) {
        layoutSync = enabled;
        if (!enabled) {
            layoutEntities.clear();
            return;
        }
        requestLayout();
    }

    /** Push one local preference into its Home Assistant control, if that control exists. */
    public void publishLayout(String setting) {
        if (!HaLayout.known(setting)) return;
        String option;
        String entity;
        boolean switchEntity;
        synchronized (this) {
            if (!layoutSync || !authenticated || socket == null) return;
            entity = layoutEntities.get(setting);
            if (entity == null) return;
            option = HaLayout.wireValue(new OverlayPreferences(context), setting);
            if (option == null) return;
            switchEntity = entity.startsWith("switch.");
            try {
                JSONObject call = new JSONObject();
                call.put("id", commandId++);
                call.put("type", "call_service");
                call.put("domain", switchEntity ? "switch" : "select");
                call.put("service", switchEntity ? ("on".equals(option) ? "turn_on" : "turn_off") : "select_option");
                JSONObject target = new JSONObject();
                target.put("entity_id", entity);
                call.put("target", target);
                JSONObject data = new JSONObject();
                data.put("entity_id", entity);
                if (!switchEntity) data.put("option", option);
                call.put("service_data", data);
                socket.send(call.toString());
            } catch (Exception ignored) { }
        }
    }

    private synchronized void requestLayout() {
        if (!layoutSync || !authenticated || socket == null) return;
        try {
            if (!layoutSubscribed) {
                JSONObject subscribe = new JSONObject();
                subscribe.put("id", commandId++);
                subscribe.put("type", "subscribe_events");
                subscribe.put("event_type", "state_changed");
                if (!socket.send(subscribe.toString())) return;
                layoutSubscribed = true;
            }
            JSONObject states = new JSONObject();
            layoutStatesId = commandId++;
            states.put("id", layoutStatesId);
            states.put("type", "get_states");
            socket.send(states.toString());
        } catch (Exception ignored) { }
    }

    private void ingestStates(JSONArray states) {
        for (int i = 0; i < states.length(); i++) {
            JSONObject state = states.optJSONObject(i);
            if (state != null) ingestOne(state);
        }
    }

    private void ingestState(JSONObject data) {
        if (data == null) return;
        JSONObject state = data.optJSONObject("new_state");
        if (state == null) return;
        if (!state.has("entity_id")) {
            try {
                state.put("entity_id", data.optString("entity_id"));
            } catch (Exception ignored) {
                return;
            }
        }
        ingestOne(state);
    }

    private void ingestOne(JSONObject state) {
        String changed;
        synchronized (this) {
            if (!layoutSync) return;
            JSONObject attributes = state.optJSONObject("attributes");
            if (attributes == null) return;
            OverlayPreferences prefs = new OverlayPreferences(context);
            if (!prefs.deviceId().equals(attributes.optString("kellie_device_id"))) return;
            prefs.setRegistryDeviceId(attributes.optString("ha_registry_device_id", ""));
            String setting = attributes.optString("kellie_setting");
            if (!HaLayout.known(setting)) return;
            String entityId = state.optString("entity_id");
            if (!entityId.isEmpty()) layoutEntities.put(setting, entityId);
            int generation = attributes.optInt("kellie_generation", 0);
            if (generation <= 0) {
                publishLayout(setting);
                return;
            }
            String raw = state.optString("state");
            if ("unavailable".equals(raw) || "unknown".equals(raw) || raw.isEmpty()) return;
            changed = HaLayout.apply(prefs, setting, raw) ? setting : null;
        }
        if (changed != null) events.haLayout(changed);
    }

    public synchronized void disconnect() {
        disposed = true;
        reconnectEnabled = false;
        reconnectHandler.removeCallbacks(reconnectTask);
        reconnectScheduled = false;
        connecting = false;
        failures = 0;
        savedBaseUrl = null;
        savedToken = null;
        authenticated = false;
        conversationId = null;
        layoutSubscribed = false;
        layoutStatesId = -1;
        layoutEntities.clear();
        stopForRunEnd();
        WebSocket ws = socket;
        socket = null;
        if (ws != null) ws.close(1000, "leaving");
    }

    public void shutdown() {
        disconnect();
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
