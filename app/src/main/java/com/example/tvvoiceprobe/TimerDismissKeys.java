package com.example.tvvoiceprobe;

import android.view.KeyEvent;

/**
 * Last-resort D-pad behavior when Android TV puts focus on the ringing
 * overlay's container instead of its Dismiss button.
 *
 * A focused Button must handle its own OK click so the user can select
 * between several ringing timers using the remote.
 */
public final class TimerDismissKeys {
    private TimerDismissKeys() { }

    public static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    public static boolean shouldFallbackDismiss(int action, int keyCode,
                                                boolean ringing, boolean buttonFocused) {
        return ringing && !buttonFocused && action == KeyEvent.ACTION_DOWN
                && isConfirmKey(keyCode);
    }
}
