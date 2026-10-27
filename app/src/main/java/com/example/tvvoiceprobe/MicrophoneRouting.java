package com.example.tvvoiceprobe;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecord;

/** Android audio routing facade; no recorder is opened during preflight. */
final class MicrophoneRouting {
    private MicrophoneRouting() { }

    static String preflight(Context context, boolean allowUnverified) {
        if (allowUnverified) return null;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return null; // Unknown, not proof of absence.
        try {
            AudioDeviceInfo[] devices = audio.getDevices(AudioManager.GET_DEVICES_INPUTS);
            if (devices == null) return null;
            int[] types = new int[devices.length];
            for (int i = 0; i < devices.length; i++) types[i] = devices[i].getType();
            return MicRoutePolicy.noUsableInputs(types) ? MicRoutePolicy.NO_MIC_MESSAGE : null;
        } catch (RuntimeException ignored) {
            // Some vendor firmware misreports audio devices. Recording errors
            // will still surface, and the diagnostics screen reports routing.
            return null;
        }
    }

    static boolean unusableActualRoute(AudioRecord recorder, boolean allowUnverified) {
        if (allowUnverified || recorder == null) return false;
        try {
            AudioDeviceInfo device = recorder.getRoutedDevice();
            // Null means the route isn't known yet. Do not invent a failure.
            return device != null && MicRoutePolicy.unavailableRoute(device.getType());
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
