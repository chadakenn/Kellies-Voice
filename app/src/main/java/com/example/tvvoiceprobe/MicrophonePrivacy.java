package com.example.tvvoiceprobe;

/**
 * Chooses the foreground-service type for the voice overlay.
 * The microphone type is requested while any recorder is open: the Assist
 * pipeline or the follow-up preview. Android 13 and older cannot switch
 * types, so they stay on the microphone type.
 */
public final class MicrophonePrivacy {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_MICROPHONE = 1;
    public static final int TYPE_SPECIAL_USE = 2;

    public static final String MIC_ON = "Microphone is on for this question.";
    public static final String MIC_FOLLOW = "Microphone is on for a follow-up.";
    public static final String MIC_OFF = "Overlay open. Microphone is off until you speak.";
    public static final String MIC_STUCK =
            "Overlay open. This TV keeps the microphone indicator on until you close it.";

    private MicrophonePrivacy() {}

    public static int serviceType(int sdk, boolean capturing) {
        if (capturing) return sdk >= 29 ? TYPE_MICROPHONE : TYPE_NONE;
        if (sdk >= 34) return TYPE_SPECIAL_USE;
        if (sdk >= 29) return TYPE_MICROPHONE;
        return TYPE_NONE;
    }

    /** A rejected special-use start must not be described as the microphone being off. */
    public static int afterRejection(int attempted, boolean rejected) {
        if (rejected && attempted == TYPE_SPECIAL_USE) return TYPE_MICROPHONE;
        return attempted;
    }

    public static String notificationText(boolean capturing, int actualType) {
        return notificationText(capturing, actualType, false);
    }

    public static String notificationText(boolean capturing, int actualType, boolean followUp) {
        if (capturing) return followUp ? MIC_FOLLOW : MIC_ON;
        if (actualType == TYPE_MICROPHONE) return MIC_STUCK;
        return MIC_OFF;
    }
}
