package com.example.tvvoiceprobe;

import android.app.Activity;
import android.os.Bundle;

/** Shows the same dashboard the screensaver will use, without waiting for the TV to idle. */
public final class ScreensaverPreviewActivity extends Activity {
    private ScreensaverBoard board;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        board = new ScreensaverBoard(this, this::finish);
        setContentView(board.view());
        board.start();
    }

    @Override public void onBackPressed() {
        if (board != null && board.handleBack()) return;
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (board != null) board.stop();
        super.onDestroy();
    }
}
