package com.example.tvvoiceprobe;

/** Bounds user-entered Assist text before it reaches the authenticated HA socket. */
public final class TypedAssistInput {
    public static final int MAX_LENGTH = 500;

    private TypedAssistInput() { }

    /** Empty means invalid; no text is retained by this helper. */
    public static String clean(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH) return "";
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return "";
        }
        return value;
    }
}
