package com.example.tvvoiceprobe;

/**
 * The few fields Kellie's Voice needs from an ESPHome microWakeWord JSON file.
 * Parsing stays on the JVM so unit tests do not touch Android's JSON stubs.
 */
public final class WakeWordConfig {
    public final String phrase;
    public final String model;
    public final float probabilityCutoff;
    public final int featureStepSizeMs;
    public final int slidingWindowSize;

    private WakeWordConfig(String phrase, String model, float probabilityCutoff,
                           int featureStepSizeMs, int slidingWindowSize) {
        this.phrase = phrase;
        this.model = model;
        this.probabilityCutoff = probabilityCutoff;
        this.featureStepSizeMs = featureStepSizeMs;
        this.slidingWindowSize = slidingWindowSize;
    }

    public static WakeWordConfig parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Wake word config is empty");
        }
        if (!"micro".equals(stringField(json, "type"))) {
            throw new IllegalArgumentException("Wake word config is not a micro model");
        }
        String phrase = stringField(json, "wake_word");
        if (phrase.isEmpty() || phrase.length() > 40) {
            throw new IllegalArgumentException("Wake word phrase is missing or too long");
        }
        String model = stringField(json, "model");
        if (!model.matches("[a-z0-9_]+\\.tflite")) {
            throw new IllegalArgumentException("Wake word model filename is not allowed");
        }
        float cutoff = (float) numberField(json, "probability_cutoff");
        if (Float.isNaN(cutoff) || Float.isInfinite(cutoff) || cutoff <= 0f || cutoff > 1f) {
            throw new IllegalArgumentException("Wake word cutoff is out of range");
        }
        int step = (int) numberField(json, "feature_step_size");
        if (step < 10 || step > 40) {
            throw new IllegalArgumentException("Wake word feature step is out of range");
        }
        int window = (int) numberField(json, "sliding_window_size");
        if (window < 1 || window > 30) {
            throw new IllegalArgumentException("Wake word sliding window is out of range");
        }
        return new WakeWordConfig(phrase, model, cutoff, step, window);
    }

    private static String stringField(String json, String key) {
        int value = valueAt(json, key);
        if (value < 0 || json.charAt(value) != '"') {
            throw new IllegalArgumentException("Missing wake word field " + key);
        }
        StringBuilder out = new StringBuilder();
        for (int i = value + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return out.toString();
            if (c == '\\' || c < 32) {
                throw new IllegalArgumentException("Unsupported character in " + key);
            }
            out.append(c);
        }
        throw new IllegalArgumentException("Unclosed wake word field " + key);
    }

    private static double numberField(String json, String key) {
        int value = valueAt(json, key);
        if (value < 0) throw new IllegalArgumentException("Missing wake word field " + key);
        int end = value;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') end++;
            else break;
        }
        if (end == value) throw new IllegalArgumentException("Wake word field " + key + " is not a number");
        try {
            double parsed = Double.parseDouble(json.substring(value, end));
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new IllegalArgumentException("Wake word field " + key + " is not finite");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Wake word field " + key + " is not a number");
        }
    }

    private static int valueAt(String json, String key) {
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) return -1;
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) return -1;
        int value = colon + 1;
        while (value < json.length() && Character.isWhitespace(json.charAt(value))) value++;
        return value < json.length() ? value : -1;
    }
}
