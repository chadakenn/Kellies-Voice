package com.example.tvvoiceprobe;

import java.net.URI;
import java.util.Locale;

/** Strict URL/callback policy for opt-in HA dashboard external auth.
 * The v3 TV grant is HTTPS-only; never bridge to a Frigate or foreign host.
 */
final class HaDashboardAuthPolicy {
    private HaDashboardAuthPolicy() { }

    static String origin(String value) {
        String safe = ScreensaverDashboard.dashboardUrl(value);
        if (safe.isEmpty()) return "";
        try {
            URI uri = URI.create(safe);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                return "";
            String host = uri.getHost().toLowerCase(Locale.US);
            // No wildcard: exactly the approved HA scheme, host and port.
            int port = uri.getPort();
            return "https://" + (host.indexOf(':') >= 0 ? "[" + host + "]" : host)
                    + (port == -1 || port == 443 ? "" : ":" + port);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    static boolean sameOrigin(String page, String tvHaOrigin) {
        String expected = origin(page);
        return !expected.isEmpty() && expected.equals(origin(tvHaOrigin));
    }

    static String authenticatedUrl(String page, String tvHaOrigin) {
        if (!sameOrigin(page, tvHaOrigin)) return "";
        String lower = page.toLowerCase(Locale.US);
        if (lower.matches(".*[?&]external_auth(?:=|&|#).*")) return "";
        int fragmentAt = page.indexOf('#');
        String route = fragmentAt < 0 ? page : page.substring(0, fragmentAt);
        String fragment = fragmentAt < 0 ? "" : page.substring(fragmentAt);
        return route + (route.indexOf('?') < 0 ? "?" : "&")
                + "external_auth=1" + fragment;
    }

    static boolean callbackAllowed(String type, String callback) {
        return ("getExternalAuth".equals(type) && "externalAuthSetToken".equals(callback))
                || ("revokeExternalAuth".equals(type)
                && "externalAuthRevokeToken".equals(callback));
    }
}
