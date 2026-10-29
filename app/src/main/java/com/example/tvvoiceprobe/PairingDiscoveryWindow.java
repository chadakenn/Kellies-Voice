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
    private Thread worker;
    private volatile Socket activeSocket;

    private PairingDiscoveryWindow(Context context, ServerSocket server, String session,
                                   Runnable expiredCallback) {
        app = context.getApplicationContext();
        this.server = server;
        this.session = session;
        this.expiredCallback = expiredCallback;
        startedAt = android.os.SystemClock.elapsedRealtime();
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
    public static PairingDiscoveryWindow start(Context context, Runnable expiredCallback)
            throws IOException {
        if (context == null || expiredCallback == null) {
            throw new IllegalArgumentException("Visible pairing owner required");
        }
        SecureRandom rng = new SecureRandom();
        byte[] publicNonce = new byte[16];
        rng.nextBytes(publicNonce);
        String session = Base64.getUrlEncoder().withoutPadding().encodeToString(publicNonce);
        ServerSocket server = new ServerSocket(0, 4);
        server.setSoTimeout(600);
        PairingDiscoveryWindow window = new PairingDiscoveryWindow(context, server, session,
                expiredCallback);
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
