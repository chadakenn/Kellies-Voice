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
        /** The answer had no spoken audio, so a text-only reply is complete. */
        default void speechMissing() { }
        /**
         * AudioRecord.release has returned for this Assist run, or the run
         * ended without ever opening a recorder. Must not call back into the client.
         */
        default void captureReleased(int runId) { }
    }

    private final Context context;
    // Wake-only preconnection can be handed to the overlay without reopening HA.
    private volatile Events events;
    private final SpeechFocus listeningFocus;
    private final RunGate runs = new RunGate();
    private volatile FollowUpEar pendingEar;
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
    // One explicit wake-triggered Assist run may claim the temporary post-wake
    // PCM. Ordinary TALK, follow-up, and full-screen sessions never consume it.
    private final AtomicBoolean wakeLeadForNextRun = new AtomicBoolean(false);
    private volatile String conversationId;
    private volatile long conversationTouchedMs;
    private volatile boolean discardConversationUpdate;
    private final Map<Integer, String> layoutWrites = new HashMap<>();
    private final HaNetworkWatch networks;
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
        this.networks = new HaNetworkWatch(this.context, reconnectHandler, this::onNetworkReady);
    }

    public boolean isConnected() { return authenticated; }

    /** Rebind callbacks when an already-authenticated wake connection is claimed. */
    public void setEvents(Events receiver) {
        if (receiver == null) throw new IllegalArgumentException("Events required");
        this.events = receiver;
    }

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
            networks.start();
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
            layoutWrites.clear(); // Results from a lost socket can never arrive.
            stopForRunEnd();
            if (reconnectEnabled) scheduleReconnect();
        }
        events.connectionLost();
    }

    private synchronized void onNetworkReady() {
        if (disposed || !reconnectEnabled || savedToken == null) return;
        // onLost already happened. A socket that has not failed yet is still
        // bound to the network that just went away.
        WebSocket stale = socket;
        boolean wasLive = stale != null || authenticated || connecting;
        if (wasLive) {
            socket = null;
            authenticated = false;
            connecting = false;
            layoutSubscribed = false;
            layoutStatesId = -1;
            layoutEntities.clear();
            layoutWrites.clear(); // Do not retain unacknowledged old-transport writes.
            stopForRunEnd();
            events.connectionLost(); // Release overlay mic/listening UI on Wi-Fi recovery.
        }
        if (reconnectScheduled) {
            reconnectHandler.removeCallbacks(reconnectTask);
            reconnectScheduled = false;
        }
        if (stale != null) stale.cancel();
        openConnection();
    }

    private synchronized void scheduleReconnect() {
        if (disposed || !reconnectEnabled || reconnectScheduled) return;
        // 2, 4, 8, 16, 30 seconds with a cap; prevents rapid connection loops
        // when HA restarts or the TV's Wi-Fi is down.
        int attempt = Math.min(failures++, 4);
        long delay = RetryTiming.withJitter(Math.min(30000L, 2000L * (1L << attempt)),
                30000L, Math.random());
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
            String layoutSetting;
            synchronized (this) { layoutSetting = layoutWrites.remove(id); }
            if (layoutSetting != null) {
                if (!msg.optBoolean("success", false)) {
                    events.status("Home Assistant didn't accept the "
                            + layoutSetting.replace('_', ' ') + " change.");
                }
                return;
            }
            if (id == currentRunId && !msg.optBoolean("success", false)) {
                JSONObject error = msg.optJSONObject("error");
                // A rejected run may never send run-end. Clear UI listening
                // as well as audio state, so the TV does not get stuck.
                stopForRunEnd();
                events.processing();
                events.status(AssistReadinessPolicy.assistError(
                        error == null ? "" : error.optString("code", ""),
                        error == null ? "" : error.optString("message", "")));
                events.runEnded();
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
                    stopForRunEnd();
                    events.processing();
                    events.status("Assist did not provide a valid audio handler ID.");
                    events.runEnded();
                    break;
                }
                if (!runs.assignHandler(currentRunId, id, () -> handlerId = id)) {
                    stopForRunEnd();
                    events.processing();
                    events.status("Assist audio handler is no longer active.");
                    events.runEnded();
                    break;
                }
                events.status("Audio handler ready. Preparing microphone...");
                break;
            case "stt-start":
                if (endRequested) sendEndOfAudio(currentRunId);
                else beginCapture(currentRunId);
                break;
            case "stt-vad-end":
                endRequested = true;
                runs.stopAudio(currentRunId, () -> recording.set(false));
                events.status("Speech detected. Processing...");
                events.processing();
                break;
            case "stt-end":
                endRequested = true;
                runs.stopAudio(currentRunId, () -> recording.set(false));
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
                    if (!discardConversationUpdate && !nextConversationId.isEmpty()) {
                        conversationId = nextConversationId;
                        conversationTouchedMs = android.os.SystemClock.elapsedRealtime();
                    }
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
                else {
                    events.status("Home Assistant didn't return a TTS audio URL.");
                    events.speechMissing();
                }
                break;
            case "error":
                if (microphoneMissing) {
                    stopForRunEnd();
                    events.processing();
                    events.status(new OverlayPreferences(context).microphoneDisabled()
                            ? MicRoutePolicy.MANUALLY_DISABLED_MESSAGE
                            : MicRoutePolicy.NO_MIC_MESSAGE);
                    events.runEnded();
                    break;
                }
                stopForRunEnd();
                events.processing();
                events.status(AssistReadinessPolicy.assistError(
                        data.optString("code", ""), data.optString("message", "")));
                events.runEnded();
                break;
            case "run-end":
                discardConversationUpdate = false;
                boolean routeMissing = microphoneMissing;
                stopForRunEnd();
                events.status(routeMissing
                        ? (new OverlayPreferences(context).microphoneDisabled()
                            ? MicRoutePolicy.MANUALLY_DISABLED_MESSAGE
                            : MicRoutePolicy.NO_MIC_MESSAGE)
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

    /** One-shot opt-in: only a wake-detection overlay may consume the short wake PCM. */
    public void setWakeLeadForNextRun(boolean fromWake) {
        wakeLeadForNextRun.set(fromWake);
    }

    public synchronized boolean pipelineIdle() {
        return currentRunId < 0;
    }

    public synchronized int activeRunId() {
        return currentRunId;
    }

    public synchronized boolean talk() {
        return talk(false);
    }

    /**
     * Auto mode keeps the microphone open until Home Assistant reports
     * end of speech. The local detector is only a slower fallback when that
     * event never arrives. Manual STOP remains available.
     */
    public synchronized boolean talk(boolean autoStopOnSilence) {
        return talk(autoStopOnSilence, null);
    }

    public synchronized boolean talk(boolean autoStopOnSilence, FollowUpEar handoff) {
        if (pendingEar != null && pendingEar != handoff) pendingEar.stop();
        pendingEar = handoff;
        if (!authenticated || socket == null || currentRunId != -1
                || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingEar = null;
            if (handoff != null) handoff.stop();
            if (!authenticated || socket == null) events.status("Connect to Home Assistant first.");
            else if (currentRunId != -1) events.status("Finish the current conversation first.");
            else events.status("Microphone permission is required.");
            return false;
        }
        OverlayPreferences micSettings = new OverlayPreferences(context);
        if (MicRoutePolicy.manuallyBlocked(micSettings.microphoneDisabled(),
                micSettings.allowUnverifiedMic())) {
            pendingEar = null;
            if (handoff != null) handoff.stop();
            events.status(MicRoutePolicy.MANUALLY_DISABLED_MESSAGE);
            return false;
        }
        String routeIssue = MicrophoneRouting.preflight(context,
                micSettings.allowUnverifiedMic());
        if (routeIssue != null) {
            pendingEar = null;
            if (handoff != null) handoff.stop();
            events.status(routeIssue);
            return false;
        }
        int id = commandId++;
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("type", "assist_pipeline/run");
            request.put("start_stage", "stt");
            request.put("end_stage", "tts");
            JSONObject input = new JSONObject();
            input.put("sample_rate", 16000);
            request.put("input", input);
            if (conversationId != null && ConversationLifetime.expired(
                    conversationTouchedMs, android.os.SystemClock.elapsedRealtime())) {
                conversationId = null;
                conversationTouchedMs = 0L;
            }
            if (conversationId != null) {
                conversationTouchedMs = android.os.SystemClock.elapsedRealtime();
                request.put("conversation_id", conversationId);
            }
            // Keep the privacy/layout setting that controls HA registry identity.
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
            runs.open(id);
            // Duck YouTube before the Assist server replies and before opening
            // the microphone. The automatic fade needs a head start.
            runs.acquire(id, listeningFocus::acquire);
            if (!socket.send(request.toString())) {
                runs.cancel(id, () -> {
                    recording.set(false);
                    listeningFocus.release();
                });
                currentRunId = -1;
                pendingEar = null;
                if (handoff != null) handoff.stop();
                events.status("Couldn't send Assist request.");
                return false;
            }
            events.status("Preparing to listen...");
            return true;
        } catch (Exception e) {
            currentRunId = -1;
            pendingEar = null;
            if (handoff != null) handoff.stop();
            runs.cancel(id, () -> {
                recording.set(false);
                listeningFocus.release();
            });
            events.status("Couldn't start Assist pipeline.");
            return false;
        }
    }

    public void finishTalking() {
        int run;
        int handler;
        boolean started;
        synchronized (this) {
            if (currentRunId < 0) {
                events.status("Press TALK first.");
                return;
            }
            endRequested = true;
            run = currentRunId;
            handler = handlerId;
            started = captureStarted;
        }
        if (!started && handler >= 0) {
            sendEndOfAudio(run, handler);
        }
        if (runs.stopAudio(run, () -> recording.set(false))) {
            events.status("Finishing audio. Waiting for Home Assistant...");
        }
        if (!started) events.captureReleased(run);
    }

    private void beginCapture(int runId) {
        final int handlerAtStart;
        synchronized (this) {
            if (captureStarted || endRequested || runId != currentRunId || handlerId < 0 || disposed) return;
            captureStarted = true;
            handlerAtStart = handlerId;
        }
        new Thread(() -> {
            AudioRecord recorder = null;
            AcousticEchoCanceler echo = null;
            NoiseSuppressor noise = null;
            try {
                FollowUpEar handoff;
                synchronized (HaAssistClient.this) {
                    handoff = pendingEar;
                    pendingEar = null;
                }
                if (handoff != null && !runs.owns(runId, handlerAtStart)) {
                    handoff.stop();
                    return;
                }
                OverlayPreferences micSettings = new OverlayPreferences(context);
                if (MicRoutePolicy.manuallyBlocked(micSettings.microphoneDisabled(),
                        micSettings.allowUnverifiedMic())) {
                    microphoneMissing = true;
                    events.status(MicRoutePolicy.MANUALLY_DISABLED_MESSAGE);
                    events.processing();
                    if (handoff != null) handoff.stop();
                    return;
                }
                // Audio after Okay Nabu, recorded by the wake listener and
                // released before this recorder opens. Empty for a normal TALK.
                byte[] wakeLead = wakeLeadForNextRun.getAndSet(false)
                        ? WakeWordService.claimCommandLead(1500L) : new byte[0];
                // claimOpen blocks. The run gate stays free so STOP can cancel.
                OpenCapture open = handoff == null ? null : handoff.claimOpen();
                if (!runs.arm(runId, handlerAtStart, () -> recording.set(true))) {
                    recorder = open == null ? null : open.recorder;
                    return;
                }
                byte[] lead = open == null ? null : open.lead16k;
                int capturedRate;
                PcmCapture samples;
                boolean started;
                if (open != null && open.sameRecorder()) {
                    recorder = open.recorder;
                    capturedRate = open.rate;
                    samples = open.pcm;
                    started = runs.start(runId, handlerAtStart, () -> { });
                } else {
                    // The preview recorder was not transferred. Audio that arrives
                    // before this new recorder starts is a gap. Speech already
                    // buffered is still sent below.
                    capturedRate = 16000;
                    recorder = AssistCapture.open(16000, 6400);
                    if (recorder == null) {
                        capturedRate = 48000;
                        recorder = AssistCapture.open(48000, 19200);
                    }
                    if (recorder == null) {
                        throw new IllegalStateException(
                                "Neither 16kHz nor 48kHz microphone capture is supported");
                    }
                    AudioRecord opening = recorder;
                    try {
                        started = runs.start(runId, handlerAtStart, opening::startRecording);
                    } catch (RuntimeException failed) {
                        AssistCapture.release(recorder);
                        recorder = null;
                        throw failed;
                    }
                    samples = new PcmCapture(capturedRate);
                }
                if (!started) return;
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    throw new IllegalStateException("Microphone recording was not started");
                }
                if (MicrophoneRouting.unusableActualRoute(recorder,
                        micSettings.allowUnverifiedMic())) {
                    microphoneMissing = true;
                    events.status(MicRoutePolicy.NO_MIC_MESSAGE);
                    events.processing();
                    return; // Prevent an STT request with silent/bogus samples.
                }
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
                if (!runs.isRecording(runId, handlerAtStart)) return;
                events.status(capturedRate == PcmCapture.RATE_16K
                        ? "LISTENING. Speak toward your microphone; select STOP when done."
                        : "LISTENING. Using 48 kHz microphone, sending 16 kHz to Home Assistant.");
                events.listening();
                final String noiseMode = roomNoise;
                byte[] devicePcm = new byte[capturedRate == 16000 ? 3200 : 9600];
                byte[] pcm = new byte[devicePcm.length + 4];
                byte[] packet = new byte[pcm.length + 1];
                long observedMs = 0L;
                long voiceMs = 0L;
                long quietMs = 0L;
                boolean heardUtterance = false;
                double backgroundRms = 0.002;
                if (wakeLead.length > 0) {
                    boolean sent = runs.send(runId, handlerAtStart,
                            () -> writePcm(wakeLead, handlerAtStart, packet));
                    if (sent) {
                        heardUtterance = true;
                        observedMs = Math.min(2000L, wakeLead.length / 32L);
                    }
                }
                if (lead != null && SpeechBuffer.containsSpeech(lead, 16000)) {
                    boolean sent = runs.send(runId, handlerAtStart,
                            () -> writePcm(lead, handlerAtStart, packet));
                    if (sent) {
                        heardUtterance = true;
                        observedMs = 400L;
                    }
                }
                while (runs.isRecording(runId, handlerAtStart)) {
                    int deviceBytes = recorder.read(devicePcm, 0, devicePcm.length);
                    if (!runs.isRecording(runId, handlerAtStart)) break;
                    if (deviceBytes <= 0)
                        throw new IllegalStateException("Microphone read failed (" + deviceBytes + ")");
                    int read = samples.process(devicePcm, deviceBytes, pcm);
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
                                runs.stopAudio(runId, () -> recording.set(false));
                            }
                        }
                    }
                    final int chunk = read;
                    boolean sent = runs.send(runId, handlerAtStart, () -> {
                        WebSocket ws = socket;
                        if (ws == null) throw new IllegalStateException("Audio socket closed");
                        packet[0] = (byte) handlerAtStart;
                        System.arraycopy(pcm, 0, packet, 1, chunk);
                        if (!ws.send(ByteString.of(packet, 0, chunk + 1))) {
                            throw new IllegalStateException("Audio socket closed");
                        }
                    });
                    if (!sent) break;
                }
            } catch (Exception e) {
                if (runs.owns(runId, handlerAtStart)) {
                    events.status("Microphone streaming failed: " + e.getMessage());
                }
            } finally {
                try {
                    if (recorder != null) {
                        try { recorder.stop(); } catch (Exception ignored) { }
                    }
                    if (echo != null) echo.release();
                    if (noise != null) noise.release();
                    if (recorder != null) recorder.release();
                } finally {
                    recorder = null;
                    runs.releaseFocusIfOwner(runId, handlerAtStart, () -> {
                        recording.set(false);
                        listeningFocus.release();
                    });
                    sendEndOfAudio(runId, handlerAtStart);
                    events.captureReleased(runId);
                }
            }
        }, "ha-assist-audio").start();
    }

    private void writePcm(byte[] pcm, int handler, byte[] packet) {
        WebSocket ws = socket;
        if (ws == null || pcm == null || pcm.length == 0 || packet == null) return;
        int offset = 0;
        while (offset < pcm.length) {
            int size = Math.min(3200, pcm.length - offset);
            if (packet.length < size + 1) return;
            packet[0] = (byte) handler;
            System.arraycopy(pcm, offset, packet, 1, size);
            if (!ws.send(ByteString.of(packet, 0, size + 1))) return;
            offset += size;
        }
    }

    private void sendEndOfAudio(int runId) {
        sendEndOfAudio(runId, handlerId);
    }

    private void sendEndOfAudio(int runId, int handlerAtStart) {
        runs.endAudio(runId, handlerAtStart, () -> {
            if (!ended.compareAndSet(false, true)) return;
            WebSocket ws = socket;
            if (ws != null) ws.send(ByteString.of(new byte[] { (byte) handlerAtStart }));
        });
    }

    private void stopForRunEnd() {
        int notify = -1;
        FollowUpEar abandoned;
        synchronized (this) {
            abandoned = pendingEar;
            pendingEar = null;
            int run = currentRunId;
            boolean worker = captureStarted;
            runs.cancel(run, () -> {
                recording.set(false);
                listeningFocus.release();
            });
            if (currentRunId == run) {
                handlerId = -1;
                currentRunId = -1;
                captureStarted = false;
                endRequested = false;
            }
            if (!worker && run >= 0) notify = run;
        }
        // Do not leave a preview recorder alive if HA rejects/ends the run
        // before stt-start can hand its recorder to Assist.
        if (abandoned != null) abandoned.stop();
        if (notify >= 0) events.captureReleased(notify);
    }

    public synchronized void setLayoutSync(boolean enabled) {
        layoutSync = enabled;
        if (!enabled) {
            layoutEntities.clear();
            return;
        }
        requestLayout();
    }

    public synchronized void clearConversation() {
        conversationId = null;
        conversationTouchedMs = 0L;
        discardConversationUpdate = currentRunId >= 0;
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
            boolean textEntity = entity.startsWith("text.");
            int callId = commandId++;
            layoutWrites.put(callId, setting);
            try {
                JSONObject call = new JSONObject();
                call.put("id", callId);
                call.put("type", "call_service");
                call.put("domain", textEntity ? "text" : switchEntity ? "switch" : "select");
                call.put("service", textEntity ? "set_value" : switchEntity
                        ? ("on".equals(option) ? "turn_on" : "turn_off") : "select_option");
                JSONObject target = new JSONObject();
                target.put("entity_id", entity);
                call.put("target", target);
                JSONObject data = new JSONObject();
                data.put("entity_id", entity);
                if (textEntity) data.put("value", option);
                else if (!switchEntity) data.put("option", option);
                call.put("service_data", data);
                if (!socket.send(call.toString())) {
                    layoutWrites.remove(callId);
                    events.status("Couldn't send the " + setting.replace('_', ' ') + " change.");
                }
            } catch (Exception ignored) {
                layoutWrites.remove(callId);
                events.status("Couldn't send the " + setting.replace('_', ' ') + " change.");
            }
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
            if ("unavailable".equals(raw) || "unknown".equals(raw)
                    || (raw.isEmpty() && !HaLayout.SCREENSAVER_URL.equals(setting)
                            && !HaLayout.SCREENSAVER_CAMERAS.equals(setting))) return;
            changed = HaLayout.apply(prefs, setting, raw) ? setting : null;
        }
        if (changed != null) {
            if (HaLayout.isScreensaver(changed)) ScreensaverSettingsEffects.apply(context, changed);
            events.haLayout(changed);
        }
    }

    public synchronized void disconnect() {
        disposed = true;
        reconnectEnabled = false;
        networks.stop();
        reconnectHandler.removeCallbacks(reconnectTask);
        reconnectScheduled = false;
        connecting = false;
        failures = 0;
        savedBaseUrl = null;
        savedToken = null;
        authenticated = false;
        conversationId = null;
        conversationTouchedMs = 0L;
        discardConversationUpdate = false;
        layoutWrites.clear();
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
