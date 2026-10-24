package com.example.tvvoiceprobe;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimerDismissKeysTest {
    @Test public void commonTvRemoteConfirmKeysDismissWithoutButtonFocus() {
        for (int key : new int[] {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A
        }) {
            assertTrue(TimerDismissKeys.shouldFallbackDismiss(KeyEvent.ACTION_DOWN, key, true, false));
        }
    }

    @Test public void focusedButtonKeepsItsOwnOkClickForMultipleTimers() {
        assertFalse(TimerDismissKeys.shouldFallbackDismiss(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, true, true));
        assertFalse(TimerDismissKeys.shouldFallbackDismiss(
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, true, false));
    }

    @Test public void silentCountdownAndNavigationNeverDismiss() {
        assertFalse(TimerDismissKeys.shouldFallbackDismiss(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, false, false));
        assertFalse(TimerDismissKeys.shouldFallbackDismiss(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, true, false));
        assertFalse(TimerDismissKeys.shouldFallbackDismiss(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, true, false));
        assertFalse(TimerDismissKeys.shouldFallbackDismiss(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, true, false));
    }
}
