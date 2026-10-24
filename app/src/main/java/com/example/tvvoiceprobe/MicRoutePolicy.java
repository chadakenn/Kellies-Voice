package com.example.tvvoiceprobe;

import android.media.AudioDeviceInfo;

/**
 * The public Android AudioDeviceInfo API identifies line-in, TV audio,
 * echo-reference and loopback-type capture paths as input devices too.
 * This is an intentional allowlist of MIC-capable routes, not merely an
 * "anything except remote submix" check. An unknown/vendor route can
 * still be tried through the explicit compatibility override in Settings.
 */
public final class MicRoutePolicy {
    public static final String NO_MIC_MESSAGE =
            "No accessible microphone detected. Connect a compatible USB mic or "
            + "use a TV microphone available to third-party apps. "
            + "Open Hardware microphone diagnostics for details.";

    private MicRoutePolicy() { }

    /** Recognized Android voice capture routes (input devices, not outputs). */
    public static boolean isMicrophoneType(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return true;
            default:
                return false;
        }
    }

    /** Includes line-level, remote-submix, echo reference and unknown routes. */
    public static boolean unavailableRoute(int type) {
        return !isMicrophoneType(type);
    }

    /**
     * Null means enumeration failed/inconclusive, not confirmed no mic.
     * An empty or non-mic-only list is a reliable preflight rejection.
     */
    public static boolean noUsableInputs(int[] types) {
        if (types == null) return false;
        for (int type : types) {
            if (isMicrophoneType(type)) return false;
        }
        return true;
    }
}
