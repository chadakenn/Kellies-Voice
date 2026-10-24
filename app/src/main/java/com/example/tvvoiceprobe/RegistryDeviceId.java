package com.example.tvvoiceprobe;

/**
 * Home Assistant's device-registry id. This is not the TV's pairing UUID.
 */
public final class RegistryDeviceId {
    private RegistryDeviceId() {}

    /** @return the id Assist can use, or empty when it is missing or not a registry id. */
    public static String accepted(String id) {
        if (id == null) return "";
        String clean = id.trim();
        if (!clean.matches("[0-9a-fA-F]{32}")) return "";
        return clean;
    }
}
