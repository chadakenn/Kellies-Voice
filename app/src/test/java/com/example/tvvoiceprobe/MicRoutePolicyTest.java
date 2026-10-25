package com.example.tvvoiceprobe;

import android.media.AudioDeviceInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class MicRoutePolicyTest {
    @Test public void manualDisableOverridesPhantomBuiltInAndCompatibilityOverride() {
        // The Philips sets list a phantom built-in microphone (type 15)
        // even though AudioRecord does not capture real speech. Never assume
        // a positive type alone confirms physical voice capture.
        assertFalse(MicRoutePolicy.noUsableInputs(new int[] {
                AudioDeviceInfo.TYPE_REMOTE_SUBMIX, AudioDeviceInfo.TYPE_BUILTIN_MIC
        }));
        assertTrue(MicRoutePolicy.manuallyBlocked(true, false));
        assertTrue(MicRoutePolicy.manuallyBlocked(true, true));
        assertFalse(MicRoutePolicy.manuallyBlocked(false, false));
        assertFalse(MicRoutePolicy.manuallyBlocked(false, true));
        assertTrue(MicRoutePolicy.MANUALLY_DISABLED_MESSAGE.contains("disabled"));
        assertTrue(MicRoutePolicy.MANUALLY_DISABLED_MESSAGE.contains("re-enable"));
    }

    @Test public void philipsEchoReferenceAndLineInAreNotSpeechMicrophones() {
        // Philips 65PUS8507/12 v0.16.5 report: Echo Reference In + line_in.
        // The echo-ref value is vendor/internal; unknown values must not grant mic.
        assertTrue(MicRoutePolicy.noUsableInputs(new int[] {
                AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_UNKNOWN
        }));
        assertTrue(MicRoutePolicy.unavailableRoute(AudioDeviceInfo.TYPE_LINE_ANALOG));
        assertTrue(MicRoutePolicy.unavailableRoute(AudioDeviceInfo.TYPE_LINE_DIGITAL));
        assertTrue(MicRoutePolicy.unavailableRoute(AudioDeviceInfo.TYPE_UNKNOWN));
        assertTrue(MicRoutePolicy.noUsableInputs(new int[] {
                AudioDeviceInfo.TYPE_REMOTE_SUBMIX, AudioDeviceInfo.TYPE_LINE_ANALOG, -999
        }));
    }

    @Test public void absentInputsAreRejectedButFailedEnumerationIsNot() {
        assertTrue(MicRoutePolicy.noUsableInputs(new int[0]));
        assertFalse(MicRoutePolicy.noUsableInputs(null));
    }

    @Test public void realMicrophonesAreAcceptedEvenWithLineAndLoopback() {
        int[] good = {
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLE_HEADSET
        };
        for (int type : good) {
            assertFalse(MicRoutePolicy.noUsableInputs(new int[] {
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_REMOTE_SUBMIX, type
            }));
            assertFalse(MicRoutePolicy.unavailableRoute(type));
        }
    }

    @Test public void unknownVendorInputIsNotSilentlyTrusted() {
        assertTrue(MicRoutePolicy.noUsableInputs(new int[] { -999 }));
        assertTrue(MicRoutePolicy.unavailableRoute(-999));
    }

    @Test public void nonMicrophoneInputTypesCannotBeRoutedForVoice() {
        int[] unsupported = {
                AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
                AudioDeviceInfo.TYPE_TV_TUNER,
                AudioDeviceInfo.TYPE_FM_TUNER,
                AudioDeviceInfo.TYPE_TELEPHONY,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_AUX_LINE,
                AudioDeviceInfo.TYPE_BUS
        };
        for (int type : unsupported) {
            assertTrue(MicRoutePolicy.unavailableRoute(type));
        }
    }
}
