package com.example.tvvoiceprobe;

/** A TV-local selection: empty means let Home Assistant choose its preferred pipeline. */
public final class AssistPipelineChoice {
    public static final String PREFERRED = "";
    private AssistPipelineChoice() { }

    /** Pipeline IDs are opaque HA strings; reject control chars and oversized values. */
    public static String normalize(String id) {
        if (id == null || id.length() > 128) return PREFERRED;
        String value = id.trim();
        if (value.isEmpty()) return PREFERRED;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x21 || c > 0x7e) return PREFERRED;
        }
        return value;
    }

    public static final class Option {
        public final String id;
        public final String name;

        public Option(String id, String name) {
            this.id = normalize(id);
            String cleaned = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", " ").trim();
            this.name = cleaned.isEmpty() ? this.id : cleaned.substring(0, Math.min(70, cleaned.length()));
        }

        public String label() {
            return name.equals(id) ? name : name + " (" + id + ")";
        }
    }
}
