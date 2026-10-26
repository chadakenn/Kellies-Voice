package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreensaverRemoteCursorTest {
    @Test public void remotePointerMovesAndClicksWithinScreen() {
        ScreensaverRemoteCursor cursor = new ScreensaverRemoteCursor();
        assertEquals(500, cursor.pixelX(1000));
        assertEquals(300, cursor.pixelY(600));
        cursor.move(ScreensaverRemoteCursor.RIGHT);
        cursor.move(ScreensaverRemoteCursor.UP);
        assertTrue(cursor.pixelX(1000) > 500);
        assertTrue(cursor.pixelY(600) < 300);
        for (int i = 0; i < 100; i++) {
            cursor.move(ScreensaverRemoteCursor.RIGHT);
            cursor.move(ScreensaverRemoteCursor.DOWN);
        }
        assertTrue(cursor.pixelX(1000) < 1000);
        assertTrue(cursor.pixelY(600) < 600);
        for (int i = 0; i < 100; i++) {
            cursor.move(ScreensaverRemoteCursor.LEFT);
            cursor.move(ScreensaverRemoteCursor.UP);
        }
        assertTrue(cursor.pixelX(1000) > 0);
        assertTrue(cursor.pixelY(600) > 0);
    }
}
