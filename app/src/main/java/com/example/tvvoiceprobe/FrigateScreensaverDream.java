package com.example.tvvoiceprobe;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

/**
 * The TV screensaver. Android starts this only after Kellie's Voice is chosen
 * as the screensaver and the usual idle time has passed. It does not use the microphone.
 */
public final class FrigateScreensaverDream extends DreamService {
    private ScreensaverBoard board;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        setScreenBright(true);
        ScreensaverSignals.dreamOpened();
        board = new ScreensaverBoard(this, this::finish);
        setContentView(board.view());
        board.start();
    }

    @Override public void onDetachedFromWindow() {
        if (board != null) board.stop();
        board = null;
        ScreensaverSignals.dreamClosed();
        super.onDetachedFromWindow();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            if (board == null || !board.handleBack()) finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
