package com.example.tvvoiceprobe;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;

/** Remembers one accepted Home Assistant certificate per host. No token is stored here. */
final class HaCertificatePin {
    private static final String PREFS = "ha_cert_pin";
    private static SharedPreferences prefs;

    private HaCertificatePin() { }

    static synchronized void attach(Context context) {
        if (prefs == null && context != null) {
            prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }

    static synchronized String pinFor(String host) {
        if (prefs == null || host == null) return "";
        return prefs.getString(key(host), "");
    }

    static synchronized void offer(String host, String sha256) {
        if (prefs == null || host == null || sha256 == null || sha256.isEmpty()) return;
        if (sha256.equals(pinFor(host))) return;
        prefs.edit().putString("offer_host", host).putString("offer_sha", sha256).apply();
    }

    static synchronized String offeredHost() {
        return prefs == null ? "" : prefs.getString("offer_host", "");
    }

    static synchronized String offeredPin() {
        return prefs == null ? "" : prefs.getString("offer_sha", "");
    }

    static synchronized void acceptOffer() {
        if (prefs == null) return;
        String host = offeredHost();
        String pin = offeredPin();
        if (host.isEmpty() || pin.isEmpty()) return;
        prefs.edit().putString(key(host), pin).remove("offer_host").remove("offer_sha").apply();
    }

    static String fingerprint(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String key(String host) {
        return "pin_" + host.trim().toLowerCase(java.util.Locale.US);
    }
}
