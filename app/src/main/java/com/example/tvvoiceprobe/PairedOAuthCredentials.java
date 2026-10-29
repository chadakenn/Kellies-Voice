package com.example.tvvoiceprobe;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * In-memory representation of OAuth credentials received ONLY after an
 * authenticated sealed #193 pairing exchange. This is NOT proof of consent
 * or permission to commit credentials as an active login.
 *
 * No toString() override: secrets must never be printed in logs.
 */
public final class PairedOAuthCredentials {
    public final String serverUrl;
    public final String clientId;
    public final String accessToken;
    public final String refreshToken;
    public final String session;
    public final String tvId;
    public final long accessExpiresAtMillis;
    public final long stagedAtMillis;

    public static final long STAGE_TTL_MILLIS = 120_000L;

    public PairedOAuthCredentials(String serverUrl, String clientId, String accessToken,
                                  String refreshToken, String session, String tvId,
                                  long accessExpiresAtMillis, long stagedAtMillis) {
        if (stagedAtMillis < 1 || accessExpiresAtMillis <= stagedAtMillis ||
                accessExpiresAtMillis - stagedAtMillis > 86_400_000L) {
            throw new IllegalArgumentException("Invalid OAuth credential time");
        }
        if (!canonicalOrigin(serverUrl) || !canonicalClientId(clientId, serverUrl)) {
            throw new IllegalArgumentException("Invalid Home Assistant origin");
        }
        if (!validSecret(accessToken) || !validSecret(refreshToken)) {
            throw new IllegalArgumentException("Invalid pairing credentials");
        }
        if (!validSession(session) || !validTvId(tvId)) {
            throw new IllegalArgumentException("Invalid TV binding");
        }
        this.serverUrl = serverUrl;
        this.clientId = clientId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.session = session;
        this.tvId = tvId;
        this.accessExpiresAtMillis = accessExpiresAtMillis;
        this.stagedAtMillis = stagedAtMillis;
    }

    private static boolean canonicalOrigin(String value) {
        if (value == null || value.length() > 256 || value.indexOf('\\') >= 0) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || host == null || host.isEmpty()
                    || (!"https".equals(scheme) && !"http".equals(scheme))
                    || uri.getRawUserInfo() != null || port == 0
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath() == null || uri.getRawPath().isEmpty()
                         || "/".equals(uri.getRawPath()))) return false;
            // All public HA transports must use HTTPS. A plain HTTP origin
            // may be recorded for a locally encrypted provisioning session,
            // but live OAuth refresh MUST use a TLS or pinned transport.
            if ("http".equals(scheme) && !localHost(host)) return false;
            return value.equals(uri.toASCIIString());
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean localHost(String host) {
        String value = host.toLowerCase(Locale.US);
        if ("localhost".equals(value) || value.endsWith(".local")
                || value.endsWith(".home.arpa") || "::1".equals(value)) {
            return true;
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) return false;
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (octets[i].isEmpty() || octets[i].length() > 3 ||
                    (octets[i].length() > 1 && octets[i].charAt(0) == '0')) return false;
            try { n[i] = Integer.parseInt(octets[i]); }
            catch (NumberFormatException error) { return false; }
            if (n[i] > 255 || n[i] < 0) return false;
        }
        return n[0] == 10 || n[0] == 127 ||
                (n[0] == 172 && n[1] >= 16 && n[1] <= 31) ||
                (n[0] == 192 && n[1] == 168);
    }

    private static boolean canonicalClientId(String clientId, String serverUrl) {
        if (clientId == null) return false;
        String expected = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                clientId.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static boolean validSecret(String value) {
        return value != null && value.length() >= 16 && value.length() <= 2048 &&
                value.matches("[A-Za-z0-9._~+/=\\-]+");
    }

    private static boolean validSession(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{22}")) return false;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 16 &&
                    Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean validTvId(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.version() == 4 && parsed.variant() == 2
                    && parsed.toString().equals(value);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    /** Requires the exact approved TV and active pairing session. */
    public boolean matches(String expectedSession, String expectedTvId, long nowMillis) {
        if (nowMillis < stagedAtMillis
                || nowMillis - stagedAtMillis >= STAGE_TTL_MILLIS
                || nowMillis >= accessExpiresAtMillis
                || expectedSession == null || expectedTvId == null) return false;
        return MessageDigest.isEqual(session.getBytes(StandardCharsets.US_ASCII),
                expectedSession.getBytes(StandardCharsets.US_ASCII))
                && MessageDigest.isEqual(tvId.getBytes(StandardCharsets.US_ASCII),
                expectedTvId.getBytes(StandardCharsets.US_ASCII));
    }
}
