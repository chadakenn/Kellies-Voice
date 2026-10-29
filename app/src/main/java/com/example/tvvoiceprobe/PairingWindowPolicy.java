package com.example.tvvoiceprobe;

/** Pure, fail-closed rules for an explicitly requested, discovery-only LAN window. */
public final class PairingWindowPolicy {
    public static final long LIFETIME_MILLIS = 120_000L;
    public static final int MAX_PROBES = 32;
    public static final String SERVICE_TYPE = "_kellies-pair._tcp.";
    public static final String CAPABILITIES_PATH = "/v1/pair/capabilities";

    private PairingWindowPolicy() {}

    public static boolean active(long startedAt, long now, int served) {
        return startedAt >= 0 && now >= startedAt
                && now - startedAt < LIFETIME_MILLIS
                && served >= 0 && served < MAX_PROBES;
    }

    /** Do not accept POST, query strings, client-supplied identifiers or alternate routes. */
    public static boolean permittedRequest(String requestLine) {
        return ("GET " + CAPABILITIES_PATH + " HTTP/1.1").equals(requestLine)
                || ("GET " + CAPABILITIES_PATH + " HTTP/1.0").equals(requestLine);
    }
}
