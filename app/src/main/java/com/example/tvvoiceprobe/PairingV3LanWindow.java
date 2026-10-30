package com.example.tvvoiceprobe;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.json.JSONObject;

/**
 * On-demand, token-free v3 LAN listener. It does NOT touch existing v1/v2
 * listeners, OAuth, credentials, Assist, mic or saved Home Assistant URL.
 *
 * This class is NOT yet called by the TV setup screen. It must only be
 * started by an explicit, visible remote click; the owning Activity MUST
 * close it on background, Back, cancel or destroy.
 *
 * Never route an OAuth code or bearer token through this HTTP socket.
 */
public final class PairingV3LanWindow implements AutoCloseable {
    private static final int MAX_REQUESTS = 80;
    private static final int BODY_LIMIT = 512;
    private static final int HEADER_LIMIT = 1024;
    private static final int DEADLINE_MS = 1500;
    // Android NSD expects the registration type, not the full mDNS FQDN.
    // Home Assistant still discovers _kellies-pair._tcp.local.
    private static final String SERVICE = "_kellies-pair._tcp.";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger requests = new AtomicInteger();
    private final ServerSocket listener;
    private final NsdManager nsd;
    private final NsdManager.RegistrationListener registration;
    private final PairingV3TvWireState state;
    private final Consumer<String> codeListener;
    private final Consumer<String> statusListener;
    private final Consumer<String> expiredCallback;
    private final String session;
    private final long startedAt;
    private Thread worker;
    private volatile Socket activeSocket;

    private PairingV3LanWindow(Context context, ServerSocket socket, String session,
                               PairingV3TvWireState state,
                               Consumer<String> codeListener,
                               Consumer<String> statusListener,
                               Consumer<String> expiredCallback) {
        this.listener = socket;
        this.session = session;
        this.state = state;
        this.codeListener = codeListener;
        this.statusListener = statusListener;
        this.expiredCallback = expiredCallback;
        this.startedAt = SystemClock.elapsedRealtime();
        this.nsd = (NsdManager) context.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE);
        this.registration = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo info) {
                main.post(() -> {
                    if (!closed.get()) {
                        statusListener.accept(
                                "V3 network discovery READY. Open Home Assistant > "
                                + "Settings > Devices & services and select "
                                + "Kellie's Voice v3 SAFE TEST.");
                    }
                });
            }
            @Override public void onRegistrationFailed(NsdServiceInfo info, int code) {
                main.post(() -> expire(
                        "Android NSD registration FAILED (code " + code
                        + "). The TV never became discoverable. Try START again."));
            }
            @Override public void onServiceUnregistered(NsdServiceInfo info) { }
            @Override public void onUnregistrationFailed(NsdServiceInfo info, int code) { }
        };
    }

    /**
     * Caller MUST be a foreground TV onboarding screen after physical remote
     * START. Each listener has a fresh P-256 key, random session and nonce.
     */
    public static PairingV3LanWindow start(Context context,
            Consumer<String> verifiedCodeOnMainThread,
            Consumer<String> statusOnMainThread,
            Consumer<String> expiredOnMainThread) throws IOException {
        if (context == null || verifiedCodeOnMainThread == null
                || statusOnMainThread == null || expiredOnMainThread == null) {
            throw new IllegalArgumentException("A visible TV setup screen is required");
        }
        byte[] sid = new byte[16];
        new SecureRandom().nextBytes(sid);
        String session = Base64.getUrlEncoder().withoutPadding().encodeToString(sid);
        KeyPair keys;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            keys = generator.generateKeyPair();
        } catch (Exception error) {
            throw new IOException("TV pairing P-256 unavailable", error);
        }
        PairingV3TvWireState state = new PairingV3TvWireState(
            keys, session, new OverlayPreferences(context).deviceId(),
            SystemClock::elapsedRealtime);

        ServerSocket server = new ServerSocket(0, 4);
        server.setSoTimeout(600);
        PairingV3LanWindow window = new PairingV3LanWindow(
            context, server, session, state,
            verifiedCodeOnMainThread, statusOnMainThread, expiredOnMainThread);
        if (window.nsd == null) {
            window.close();
            throw new IOException("Local network discovery unavailable");
        }
        try {
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceType(SERVICE);
            info.setServiceName("Kellie's Voice TV setup v3");
            info.setPort(server.getLocalPort());
            info.setAttribute("protocol", "3");
            info.setAttribute("mode", "verified-code-only");
            info.setAttribute("session", session);
            window.nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD,
                    window.registration);
            window.worker = new Thread(window::serve, "KellieV3PairingLAN");
            window.worker.setDaemon(true);
            window.worker.start();
            window.main.postDelayed(
                    () -> window.expire("The full 2-minute window expired. Press START to retry."),
                    PairingV3TvWireState.TIMEOUT_MS);
            return window;
        } catch (RuntimeException error) {
            window.close();
            throw new IOException("Could not start v3 pairing discovery", error);
        }
    }

    public int port() { return listener.getLocalPort(); }
    public String session() { return session; }

    /** Invoked ONLY from the TV's on-screen physical remote OK handler. */
    public boolean approveFromPhysicalRemote() {
        if (closed.get()) return false;
        try {
            state.approveFromPhysicalTvRemote(session);
            return true;
        } catch (RuntimeException error) {
            close();
            return false;
        }
    }

    private void expire(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        closeResources();
        expiredCallback.accept(reason);
    }

    private void serve() {
        while (!closed.get()) {
            if (SystemClock.elapsedRealtime() - startedAt
                    >= PairingV3TvWireState.TIMEOUT_MS) {
                main.post(() -> expire("V3 test expired after 2 minutes."));
                return;
            }
            if (requests.get() >= MAX_REQUESTS) {
                main.post(() -> expire("V3 test request limit reached. Press START to retry."));
                return;
            }
            try (Socket socket = listener.accept()) {
                activeSocket = socket;
                socket.setSoTimeout(650);
                if (requests.incrementAndGet() > MAX_REQUESTS) {
                    main.post(() -> expire("V3 test request limit reached. Press START to retry."));
                    return;
                }
                handle(socket);
            } catch (SocketTimeoutException ignored) {
                // Short accept/read timeout prevents untrusted LAN stalls.
            } catch (IOException ignored) {
                if (closed.get()) return;
            } finally {
                activeSocket = null;
            }
        }
    }

    private void handle(Socket socket) throws IOException {
        String firstLine = boundedLine(socket, socket.getInputStream(),
                SystemClock.elapsedRealtime() + DEADLINE_MS, 256);
        try {
            if ("POST /v3/pair/commit HTTP/1.1".equals(firstLine)) {
                JSONObject payload = readJson(socket);
                require(payload, "ha_commit");
                byte[] commit = b64(payload.getString("ha_commit"), 32);
                byte[] tvCommit = state.acceptHaCommit(session, commit);
                answer(socket, 200, base()
                        .put("tv_commit", encode(tvCommit)));
            } else if ("POST /v3/pair/reveal-ha HTTP/1.1".equals(firstLine)) {
                JSONObject payload = readJson(socket);
                require(payload, "ha_key", "ha_nonce");
                byte[] haKey = b64(payload.getString("ha_key"), -1);
                byte[] haNonce = b64(payload.getString("ha_nonce"), 32);
                state.acceptHaReveal(session, haKey, haNonce);
                answer(socket, 200, base().put("ha_verified", true));
            } else if (("GET /v3/pair/reveal-tv?protocol=3&session=" + session
                    + " HTTP/1.1").equals(firstLine)) {
                readGetHeaders(socket);
                PairingV3TvWireState.TvReveal reveal = state.revealTv(session);
                String code = state.codeForTv(session);
                main.post(() -> { if (!closed.get()) codeListener.accept(code); });
                answer(socket, 200, base().put("tv_key", encode(reveal.tvKey))
                        .put("tv_nonce", encode(reveal.tvNonce)));
            } else if (("GET /v3/pair/status?protocol=3&session=" + session
                    + " HTTP/1.1").equals(firstLine)) {
                readGetHeaders(socket);
                PairingV3TvWireState.Approval result = state.approvedStatus(session);
                JSONObject payload = base().put("approved", result != null);
                if (result != null) {
                    payload.put("tv_uuid", result.tvUuid);
                    payload.put("proof", result.proof);
                }
                answer(socket, 200, payload);
            } else if ("POST /v3/pair/owner-receipt HTTP/1.1".equals(firstLine)) {
                // Accept only an authenticated, owner-approved and session-bound
                // *encrypted* receipt. No tokens, credentials or generic API.
                JSONObject payload = readJson(socket);
                if (payload.length() != 4
                        || !(payload.opt("protocol") instanceof Integer)
                        || payload.getInt("protocol") != 3
                        || !session.equals(payload.optString("session", ""))
                        || !(payload.opt("sequence") instanceof Integer)
                        || !(payload.opt("receipt") instanceof String)) {
                    throw new IOException("Invalid v3 owner receipt request");
                }
                int seq = payload.getInt("sequence");
                byte[] encrypted = b64(payload.getString("receipt"), 30);
                PairingV3QaPingChannel.Packet ack =
                        state.consumeOwnerReceipt(session, seq, encrypted);
                answer(socket, 200, base()
                        .put("ack_seq", ack.sequence)
                        .put("ack", encode(ack.ciphertext)));
                main.post(() -> {
                    if (!closed.get()) statusListener.accept(
                        "SECURE OWNER APPROVAL VERIFIED on this TV. "
                        + "Encrypted TV acknowledgement sent to Home Assistant. "
                        + "NO HOME ASSISTANT LOGIN OR TOKEN WAS CREATED.");
                });
            } else {
                // No v2 route, no redirect to v2, no credential handler.
                answer(socket, 404, errorJson("not_available"));
            }
        } catch (Exception error) {
            answer(socket, 400, errorJson("v3_pairing_rejected"));
        }
    }

    private static JSONObject errorJson(String value) {
        JSONObject result = new JSONObject();
        try {
            result.put("error", value);
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException("Cannot format v3 error");
        }
        return result;
    }

    private JSONObject base() throws Exception {
        return new JSONObject().put("protocol", 3).put("session", session);
    }

    private void require(JSONObject doc, String... other) throws Exception {
        if (doc.length() != other.length + 2
                || !(doc.opt("protocol") instanceof Integer)
                || doc.getInt("protocol") != 3
                || !(doc.opt("session") instanceof String)
                || !session.equals(doc.getString("session"))) {
            throw new IOException("Unsupported v3 or session");
        }
        for (String field : other) {
            if (!(doc.opt(field) instanceof String)) {
                throw new IOException("Invalid v3 field");
            }
        }
    }

    /** Headers and JSON must be bounded, nonchunked and unambiguous. */
    private static JSONObject readJson(Socket socket) throws Exception {
        InputStream input = socket.getInputStream();
        long deadline = SystemClock.elapsedRealtime() + DEADLINE_MS;
        int length = -1, count = 0;
        boolean ended = false;
        for (int i = 0; i < 16; i++) {
            String h = boundedLine(socket, input, deadline, 256);
            count += h.length() + 2;
            if (count > HEADER_LIMIT) throw new IOException("Headers too large");
            if (h.isEmpty()) { ended = true; break; }
            String lower = h.toLowerCase(Locale.ROOT);
            if (lower.startsWith("transfer-encoding:") || lower.startsWith("expect:")) {
                throw new IOException("Transfer encoding or expect forbidden");
            }
            if (lower.startsWith("content-length:")) {
                if (length >= 0) throw new IOException("Duplicate length");
                String raw = h.substring(h.indexOf(':') + 1).trim();
                if (!raw.matches("[0-9]{1,3}")) throw new IOException("Length invalid");
                length = Integer.parseInt(raw);
            }
        }
        if (!ended || length < 1 || length > BODY_LIMIT) {
            throw new IOException("Invalid v3 request length");
        }
        byte[] bytes = new byte[length];
        int at = 0;
        while (at < length) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new SocketTimeoutException();
            socket.setSoTimeout((int) Math.min(650, remaining));
            int n = input.read(bytes, at, length - at);
            if (n < 0) throw new IOException("Truncated request");
            at += n;
        }
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    private static void readGetHeaders(Socket socket) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + DEADLINE_MS;
        int size = 0;
        boolean ended = false;
        for (int i = 0; i < 16; i++) {
            String h = boundedLine(socket, socket.getInputStream(), deadline, 256);
            size += h.length() + 2;
            if (size > HEADER_LIMIT) throw new IOException("Headers too large");
            if (h.isEmpty()) { ended = true; break; }
            String lower = h.toLowerCase(Locale.ROOT);
            if (lower.startsWith("transfer-encoding:")
                    || lower.startsWith("content-length:")
                    || lower.startsWith("expect:")) {
                throw new IOException("Unexpected GET request body");
            }
        }
        if (!ended) throw new IOException("Truncated GET headers");
    }

    private static String boundedLine(Socket socket, InputStream in,
                                      long deadline, int max) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int i = 0; i < max; i++) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new SocketTimeoutException();
            socket.setSoTimeout((int) Math.min(650, remaining));
            int value = in.read();
            if (value < 0) throw new IOException("Truncated HTTP line");
            if (value == '\n') {
                String text = data.toString("US-ASCII");
                if (!text.endsWith("\r")) throw new IOException("Invalid CRLF");
                return text.substring(0, text.length() - 1);
            }
            if (value < 32 && value != '\r') throw new IOException("Invalid HTTP bytes");
            data.write(value);
        }
        throw new IOException("Overlong HTTP line");
    }

    private static byte[] b64(String encoded, int requiredBytes) throws IOException {
        if (encoded == null || !encoded.matches("[A-Za-z0-9_-]+")) {
            throw new IOException("Invalid v3 binary");
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(encoded);
            if (!encode(raw).equals(encoded)
                    || requiredBytes >= 0 && raw.length != requiredBytes
                    || raw.length > 150) throw new IOException("V3 binary invalid");
            return raw;
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid v3 base64");
        }
    }

    private static String encode(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static void answer(Socket socket, int status, JSONObject json)
            throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > 1024) throw new IOException("Response too big");
        String header = "HTTP/1.1 " + (status == 200 ? "200 OK" :
                status == 404 ? "404 Not Found" : "400 Bad Request")
                + "\r\nContent-Type: application/json; charset=utf-8"
                + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store"
                + "\r\nX-Content-Type-Options: nosniff"
                + "\r\nConnection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeResources();
    }

    private void closeResources() {
        main.removeCallbacksAndMessages(null);
        state.close();
        try { listener.close(); } catch (IOException ignored) { }
        Socket socket = activeSocket;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        if (nsd != null) {
            try { nsd.unregisterService(registration); }
            catch (RuntimeException ignored) { }
        }
    }
}
