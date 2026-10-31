package com.example.tvvoiceprobe;

import java.net.URI;

/**
 * User-selected local HTTP screen URL eligibility, never a credential policy.
 *
 * Opt-in strict LAN mode only accepts numeric RFC1918 IPv4 literals. This
 * avoids DNS name rebinding and accidentally opening a public HTTP host.
 * Other URL modes keep their earlier behavior, including HTTPS v3 login.
 *
 * WARNING: Even on RFC1918, HTTP traffic is plaintext and unauthenticated.
 * Browsers may request external subresources; no native HA access or refresh
 * token is provided by this local HTTP viewer.
 */
final class LanHttpDashboardPolicy {
    private LanHttpDashboardPolicy() { }

    static boolean isStrictLanHttp(String value) {
        String candidate = ScreensaverDashboard.dashboardUrl(value);
        if (candidate.isEmpty()) return false;
        try {
            URI uri = new URI(candidate);
            if (!"http".equalsIgnoreCase(uri.getScheme())) return false;
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) return false;
            return isPrivateIPv4(uri.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Numeric IPv4 only, no ambiguous octal, DNS, loopback or CGNAT. */
    private static boolean isPrivateIPv4(String host) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) return false;
        int[] b = new int[4];
        for (int i = 0; i < 4; i++) {
            String str = octets[i];
            if (str.isEmpty() || str.length() > 3
                    || (str.length() > 1 && str.charAt(0) == '0')) return false;
            for (int j = 0; j < str.length(); j++)
                if (str.charAt(j) < '0' || str.charAt(j) > '9') return false;
            try { b[i] = Integer.parseInt(str); }
            catch (NumberFormatException ignored) { return false; }
            if (b[i] < 0 || b[i] > 255) return false;
        }
        return b[0] == 10 || b[0] == 172 && b[1] >= 16 && b[1] <= 31
                || b[0] == 192 && b[1] == 168;
    }

    /** A strict LAN browsing session stays on its chosen URL origin.
     * This checks main-frame navigation; arbitrary subresource traffic is
     * not guaranteed local, so users should trust their HA custom cards.
     */
    static boolean allowedNavigation(String initial, String next) {
        return isStrictLanHttp(initial) && isStrictLanHttp(next)
                && ScreensaverDashboard.sameSite(initial, next);
    }
}
