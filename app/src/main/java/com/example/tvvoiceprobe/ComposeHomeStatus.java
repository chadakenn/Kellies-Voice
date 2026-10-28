package com.example.tvvoiceprobe;

import java.net.URI;

/**
 * Display-only helpers for Compose Home Assistant settings.
 * Never returns a token, userinfo, query, or fragment.
 */
public final class ComposeHomeStatus {
    private ComposeHomeStatus() { }

    public static String configuredLabel(boolean configured) {
        return configured ? "Configured" : "Not configured";
    }

    /**
     * Host (and port, if present) only. Returns empty when the address is
     * missing, malformed, or contains userinfo / query / fragment material.
     */
    public static String addressSummary(String url) {
        if (url == null) return "";
        String value = url.trim();
        if (value.isEmpty() || value.length() > 500) return "";
        if (value.indexOf('@') >= 0) return "";
        try {
            URI uri = new URI(value);
            if (uri.getUserInfo() != null) return "";
            String query = uri.getRawQuery();
            String fragment = uri.getRawFragment();
            if (secretMaterial(query) || secretMaterial(fragment)) return "";
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return "";
            int port = uri.getPort();
            return port > 0 ? host + ":" + port : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean summaryHidesSecrets(String summary, String token) {
        if (summary == null) return true;
        if (token != null && !token.isEmpty() && summary.contains(token)) return false;
        String lower = summary.toLowerCase();
        return !lower.contains("token") && !lower.contains("password")
                && !lower.contains("secret") && !lower.contains("@");
    }

    private static boolean secretMaterial(String value) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase();
        return lower.contains("token") || lower.contains("password")
                || lower.contains("secret") || lower.contains("api_key")
                || lower.contains("apikey") || lower.contains("authorization");
    }
}
