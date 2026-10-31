package com.example.tvvoiceprobe;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

/** Exact, fail-closed metadata for a future HA scoped renewable-login grant.
 * No secret and no network. A normal HA non-admin user is NOT necessarily scoped.
 */
final class TvRenewableLoginPolicy {
    private TvRenewableLoginPolicy() { }

    static final class Grant {
        final String haOrigin;
        final String tvUuid;
        final String clientId;
        final String tokenId;

        Grant(String origin, String uuid, String client, String id) {
            this.haOrigin = origin;
            this.tvUuid = uuid;
            this.clientId = client;
            this.tokenId = id;
        }
    }

    static Grant check(String origin, String uuid, String client, String tokenId) {
        if (origin == null || uuid == null || client == null || tokenId == null
                || !uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || !tokenId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Restricted TV grant invalid");
        }
        final URI parsed;
        try {
            parsed = URI.create(origin);
            if (!"https".equals(parsed.getScheme())
                    || parsed.getHost() == null || parsed.getUserInfo() != null
                    || parsed.getQuery() != null || parsed.getFragment() != null
                    || (parsed.getPath() != null
                        && !parsed.getPath().isEmpty() && !"/".equals(parsed.getPath()))
                    || parsed.getPort() == 0 || !origin.equals(origin.trim())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException problem) {
            throw new IllegalArgumentException("HTTPS-only HA grant required");
        }
        String canonical = origin.endsWith("/")
                ? origin.substring(0, origin.length() - 1) : origin;
        String root = canonical + "/kellies-voice/tv/" + uuid + "/attempt/";
        if (!client.startsWith(root)
                || !client.substring(root.length()).matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Wrong TV client ID");
        }
        return new Grant(canonical, uuid, client, tokenId);
    }

    static void checkCredential(String refreshToken, String accessToken, long lifetime) {
        if (refreshToken == null || refreshToken.isEmpty()
                || refreshToken.length() > 4096 || refreshToken.contains("\n")
                || accessToken == null || accessToken.isEmpty()
                || accessToken.length() > 8192 || accessToken.contains("\n")
                || lifetime < 30 || lifetime > 86400) {
            throw new IllegalArgumentException("Invalid token data");
        }
    }

    static String tokenUrl(Grant grant) {
        return grant.haOrigin + "/auth/token";
    }
}
