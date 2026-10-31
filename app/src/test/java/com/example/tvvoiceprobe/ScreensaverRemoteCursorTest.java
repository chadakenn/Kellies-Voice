package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreensaverRemoteCursorTest {
    @Test public void eachBrowsingSessionCanResetPointerToVisibleCenter() {
        ScreensaverRemoteCursor pointer = new ScreensaverRemoteCursor();
        for (int i = 0; i < 20; i++) {
            pointer.move(ScreensaverRemoteCursor.RIGHT);
            pointer.move(ScreensaverRemoteCursor.DOWN);
        }
        assertTrue(pointer.pixelX(1920) > 960);
        assertTrue(pointer.pixelY(1080) > 540);
        pointer.reset();
        assertEquals(960, pointer.pixelX(1920));
        assertEquals(540, pointer.pixelY(1080));
        pointer.move(ScreensaverRemoteCursor.DOWN);
        assertEquals(621, pointer.pixelY(1080));
    }

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
