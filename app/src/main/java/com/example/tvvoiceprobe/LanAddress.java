package com.example.tvvoiceprobe;

import java.net.InetAddress;
import java.util.Locale;

/**
 * Cleartext is allowed only for a loopback or private host. Android's
 * network security config cannot express an IP range, so callers check this
 * before opening HTTP.
 */
public final class LanAddress {
    private LanAddress() { }

    public static boolean cleartextAllowed(String host) {
        if (host == null) return false;
        String name = host.trim().toLowerCase(Locale.US);
        if (name.isEmpty()) return false;
        while (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        if ("localhost".equals(name) || "localhost.localdomain".equals(name)) return true;
        if (name.endsWith(".local") || name.endsWith(".lan") || name.endsWith(".home")
                || name.endsWith(".internal") || name.endsWith(".home.arpa")) return true;
        if (name.indexOf(':') >= 0 || looksLikeIpv4(name)) return privateIp(name);
        return false;
    }

    private static boolean looksLikeIpv4(String name) {
        int dots = 0;
        for (int i = 0; i < name.length(); i++) if (name.charAt(i) == '.') dots++;
        return dots == 3;
    }

    private static boolean privateIp(String name) {
        try {
            byte[] address = InetAddress.getByName(name).getAddress();
            if (address.length == 4) {
                int a = address[0] & 0xff;
                int b = address[1] & 0xff;
                if (a == 10 || a == 127) return true;
                if (a == 192 && b == 168) return true;
                if (a == 169 && b == 254) return true;
                return a == 172 && b >= 16 && b <= 31;
            }
            if (address.length == 16) {
                if (isZero(address) && (address[15] & 0xff) == 1) return true;
                int first = address[0] & 0xff;
                if ((first & 0xfe) == 0xfc) return true;
                return (first & 0xff) == 0xfe && (address[1] & 0xc0) == 0x80;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static boolean isZero(byte[] address) {
        for (int i = 0; i < 15; i++) if (address[i] != 0) return false;
        return true;
    }
}
