package com.example.tvvoiceprobe;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Discovery-only stage for #193. This is NOT a pairing/authorization service.
 *
 * Only an on-screen user action creates this short-lived listener. It serves
 * one public, read-only capability document; it never receives, stores or
 * transfers a Home Assistant URL, access token, OAuth code or user identity.
 * Production's legacy mDNS advertisement is unchanged. No background service.
 */
public final class PairingDiscoveryWindow implements AutoCloseable {
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger served = new AtomicInteger();
    private final ServerSocket server;
    private final NsdManager nsd;
    private final NsdManager.RegistrationListener registration;
    private final long startedAt;
    private final String session;
    private final Runnable expiredCallback;
    private final java.util.function.Consumer<String> codeListener;
    private final PairingHandshakeState handshake;
    private Thread worker;
    private volatile Socket activeSocket;

    private PairingDiscoveryWindow(Context context, ServerSocket server, String session,
                                   Runnable expiredCallback,
                                   java.util.function.Consumer<String> codeListener) {
        app = context.getApplicationContext();
        this.server = server;
        this.session = session;
        this.expiredCallback = expiredCallback;
        this.codeListener = codeListener;
        startedAt = android.os.SystemClock.elapsedRealtime();
        handshake = new PairingHandshakeState(session, startedAt);
        nsd = (NsdManager) app.getSystemService(Context.NSD_SERVICE);
        registration = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo info) { }
            @Override public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                if (closed.get()) return;
                main.post(() -> {
                    if (closed.get()) return;
                    close();
                    expiredCallback.run();
                });
            }
            @Override public void onServiceUnregistered(NsdServiceInfo info) { }
            @Override public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) { }
        };
    }

    /** Start only after an explicit tap on the visible "Start discovery" button. */
    public static PairingDiscoveryWindow start(Context context, Runnable expiredCallback,
            java.util.function.Consumer<String> codeListener)
            throws IOException {
        if (context == null || expiredCallback == null || codeListener == null) {
            throw new IllegalArgumentException("Visible pairing owner required");
        }
        SecureRandom rng = new SecureRandom();
        byte[] publicNonce = new byte[16];
        rng.nextBytes(publicNonce);
        String session = Base64.getUrlEncoder().withoutPadding().encodeToString(publicNonce);
        ServerSocket server = new ServerSocket(0, 4);
        server.setSoTimeout(600);
        PairingDiscoveryWindow window = new PairingDiscoveryWindow(context, server, session,
                expiredCallback, codeListener);
        if (window.nsd == null) {
            window.close();
            throw new IOException("Network discovery is unavailable");
        }
        try {
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceType(PairingWindowPolicy.SERVICE_TYPE);
            info.setServiceName("Kellies Voice setup");
            info.setPort(server.getLocalPort()); // Actual listener, never placeholder 45600.
            info.setAttribute("mode", "discovery-only");
            info.setAttribute("protocol", "1");
            info.setAttribute("preview", "2");
            info.setAttribute("session", session);
            window.nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, window.registration);
        } catch (RuntimeException error) {
            window.close();
            throw new IOException("Could not advertise pairing discovery", error);
        }
        window.worker = new Thread(window::serve, "KelliePairingDiscovery");
        window.worker.setDaemon(true);
        window.worker.start();
        window.main.postDelayed(window::expire, PairingWindowPolicy.LIFETIME_MILLIS);
        return window;
    }

    public int port() { return server.getLocalPort(); }

    /** Only the visible TV confirmation control can approve a pending offer. */
    public boolean approveFromTv() {
        return handshake.approve(android.os.SystemClock.elapsedRealtime());
    }

    private void expire() {
        if (closed.get()) return;
        close();
        expiredCallback.run();
    }

    private void serve() {
        while (!closed.get()) {
            if (!PairingWindowPolicy.active(startedAt,
                    android.os.SystemClock.elapsedRealtime(), served.get())) {
                main.post(this::expire);
                return;
            }
            try (Socket socket = server.accept()) {
                activeSocket = socket;
                socket.setSoTimeout(700);
                socket.setTcpNoDelay(true);
                if (served.incrementAndGet() > PairingWindowPolicy.MAX_PROBES) {
                    main.post(this::expire);
                    return;
                }
                String firstLine = firstLine(socket);
                if (PairingWindowPolicy.permittedRequest(firstLine)) {
                    String document = "{\"protocol\":1,\"mode\":\"discovery-only\","
                            + "\"authorized\":false,\"session\":\"" + session + "\"}";
                    respond(socket.getOutputStream(), "200 OK", document);
                } else if ("POST /v1/pair/offer HTTP/1.1".equals(firstLine)) {
                    try {
                        org.json.JSONObject request = readOffer(socket);
                        if (request.length() != 2) throw new IOException("Bad request fields");
                        PairingHandshakeState.Offer offer = handshake.offer(
                                request.getString("session"), request.getString("ha_key"),
                                android.os.SystemClock.elapsedRealtime());
                        main.post(() -> { if (!closed.get()) codeListener.accept(offer.code); });
                        respond(socket.getOutputStream(), "200 OK",
                                "{\"session\":\"" + session + "\",\"authorized\":false,"
                                + "\"tv_key\":\"" + offer.tvKey + "\"}");
                    } catch (Exception error) {
                        respond(socket.getOutputStream(), "400 Bad Request",
                                "{\"error\":\"pairing_offer_rejected\"}");
                    }
                } else if (("GET /v1/pair/status?session=" + session + " HTTP/1.1")
                        .equals(firstLine)) {
                    String proof = handshake.proof(android.os.SystemClock.elapsedRealtime());
                    respond(socket.getOutputStream(), "200 OK",
                            proof == null ? "{\"approved\":false}" :
                                    "{\"approved\":true,\"proof\":\"" + proof + "\"}");
                } else {
                    respond(socket.getOutputStream(), "404 Not Found",
                            "{\"error\":\"not_available\"}");
                }
            } catch (SocketTimeoutException ignored) {
                // Short read/accept timeout bounds untrusted LAN clients.
            } catch (IOException ignored) {
                if (closed.get()) return;
            } finally {
                activeSocket = null;
            }
        }
    }

    /** Parse at most 256 bytes within one second, even under a slow client. */
    private static String firstLine(Socket socket) throws IOException {
        InputStream input = socket.getInputStream();
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        long deadline = android.os.SystemClock.elapsedRealtime() + 1000L;
        for (int i = 0; i < 256; i++) {
            long remaining = deadline - android.os.SystemClock.elapsedRealtime();
            if (remaining <= 0) return "";
            socket.setSoTimeout((int) Math.min(remaining, 700L));
            int value = input.read();
            if (value < 0) return "";
            if (value == '\n') {
                String request = line.toString("US-ASCII");
                return request.endsWith("\r")
                        ? request.substring(0, request.length() - 1) : request;
            }
            if (value < 32 && value != '\r') return "";
            line.write(value);
        }
        return "";
    }

    /** Strict one-shot JSON upload: only session and public HA key, no credentials. */
    private static org.json.JSONObject readOffer(Socket socket) throws Exception {
        InputStream input = socket.getInputStream();
        long deadline = android.os.SystemClock.elapsedRealtime() + 1500L;
        int length = -1, headerBytes = 0;
        boolean ended = false;
        for (int i = 0; i < 12; i++) {
            String header = boundedLine(socket, input, deadline);
            headerBytes += header.length() + 2;
            if (headerBytes > 1024) throw new IOException("Headers too large");
            if (header.isEmpty()) { ended = true; break; }
            if (header.toLowerCase(Locale.ROOT).startsWith("transfer-encoding:"))
                throw new IOException("Transfer encoding forbidden");
            if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                if (length != -1) throw new IOException("Duplicate length");
                String value = header.substring(header.indexOf(':') + 1).trim();
                if (!value.matches("[0-9]{1,3}")) throw new IOException("Bad length");
                length = Integer.parseInt(value);
            }
        }
        if (!ended || length <= 0 || length > 400) throw new IOException("Invalid payload");
        byte[] body = new byte[length];
        for (int i = 0; i < length; i++) {
            long remaining = deadline - android.os.SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new SocketTimeoutException();
            socket.setSoTimeout((int) Math.min(700L, remaining));
            int value = input.read();
            if (value < 0) throw new IOException("Truncated JSON");
            body[i] = (byte) value;
        }
        return new org.json.JSONObject(new String(body, StandardCharsets.UTF_8));
    }

    private static String boundedLine(Socket socket, InputStream input, long deadline)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < 256; i++) {
            long remaining = deadline - android.os.SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new SocketTimeoutException();
            socket.setSoTimeout((int) Math.min(700L, remaining));
            int ch = input.read();
            if (ch < 0) throw new IOException("Truncated header");
            if (ch == '\n') {
                String line = bytes.toString("US-ASCII");
                if (!line.endsWith("\r")) throw new IOException("Bad line ending");
                return line.substring(0, line.length() - 1);
            }
            if (ch < 32 && ch != '\r') throw new IOException("Invalid header");
            bytes.write(ch);
        }
        throw new IOException("Header line too long");
    }

    private static void respond(OutputStream output, String status, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = String.format(Locale.US,
                "HTTP/1.1 %s\r\nContent-Type: application/json; charset=utf-8\r\n"
                        + "Content-Length: %d\r\nCache-Control: no-store\r\n"
                        + "X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n",
                status, payload.length);
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(payload);
        output.flush();
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        main.removeCallbacksAndMessages(null);
        handshake.close();
        try { server.close(); } catch (IOException ignored) { }
        Socket client = activeSocket;
        if (client != null) {
            try { client.close(); } catch (IOException ignored) { }
        }
        if (nsd != null) {
            try { nsd.unregisterService(registration); }
            catch (RuntimeException ignored) { }
        }
    }
}
