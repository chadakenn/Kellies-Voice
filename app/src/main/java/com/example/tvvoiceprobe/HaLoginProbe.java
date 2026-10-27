package com.example.tvvoiceprobe;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** One Home Assistant websocket login. Nothing is saved if this does not succeed. */
final class HaLoginProbe {
    static final String REJECTED = "Home Assistant rejected that token. Nothing was saved.";
    static final String NO_ANSWER = "Home Assistant didn't answer the login check. Nothing was saved.";

    private HaLoginProbe() { }

    /** Null means the token was accepted. An empty string means keep waiting. */
    static String verdict(String type) {
        if ("auth_ok".equals(type)) return null;
        if ("auth_invalid".equals(type)) return REJECTED;
        return "";
    }

    static String check(String url, String token) {
        okhttp3.HttpUrl base = okhttp3.HttpUrl.parse(url);
        if (base == null) return "Enter the full http:// or https:// Home Assistant address.";
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> verdict = new AtomicReference<>(NO_ANSWER);
        Request request = new Request.Builder()
                .url(base.newBuilder().addPathSegments("api/websocket").build())
                .build();
        WebSocket socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    String type = new JSONObject(text).optString("type");
                    if ("auth_required".equals(type)) {
                        webSocket.send(new JSONObject().put("type", "auth")
                                .put("access_token", token).toString());
                        return;
                    }
                    String answer = verdict(type);
                    if (answer == null || !answer.isEmpty()) {
                        verdict.set(answer);
                        done.countDown();
                        webSocket.close(1000, "checked");
                    }
                } catch (Exception ignored) { }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                done.countDown();
            }
        });
        try {
            done.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        socket.cancel();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        return verdict.get();
    }
}
