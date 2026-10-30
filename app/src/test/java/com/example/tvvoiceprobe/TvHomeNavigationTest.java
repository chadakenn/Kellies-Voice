package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** TV D-pad should move up from TALK into Quick Actions, not scroll blindly. */
public class TvHomeNavigationTest {
    @Test public void noShortcutsKeepsOriginalThreeButtonRow() {
        assertEquals(0, TvHomeNavigation.up(0, 0));
        assertEquals(0, TvHomeNavigation.left(0, 0));
        assertEquals(1, TvHomeNavigation.right(0, 0));
        assertEquals(2, TvHomeNavigation.right(2, 0));
        assertEquals(2, TvHomeNavigation.down(2));
    }

    @Test public void upFromToolbarGoesToClosestShortcutAndDownReturns() {
        assertEquals(3, TvHomeNavigation.up(0, 4));
        assertEquals(4, TvHomeNavigation.up(1, 4));
        assertEquals(5, TvHomeNavigation.up(2, 4));
        assertEquals(0, TvHomeNavigation.down(3));
        assertEquals(1, TvHomeNavigation.down(4));
        assertEquals(2, TvHomeNavigation.down(5));
        assertEquals(2, TvHomeNavigation.down(6));
    }

    @Test public void leftRightStaysOnItsOwnRow() {
        assertEquals(2, TvHomeNavigation.right(2, 4));
        assertEquals(3, TvHomeNavigation.left(3, 4));
        assertEquals(6, TvHomeNavigation.right(6, 4));
        assertEquals(5, TvHomeNavigation.right(4, 4));
        assertEquals(4, TvHomeNavigation.left(5, 4));
        assertEquals(0, TvHomeNavigation.left(0, 4));
    }

    @Test public void singleShortcutNeverNavigatesOutOfBounds() {
        assertEquals(3, TvHomeNavigation.up(0, 1));
        assertEquals(3, TvHomeNavigation.up(2, 1));
        assertEquals(3, TvHomeNavigation.right(3, 1));
        assertEquals(0, TvHomeNavigation.down(3));
    }
}
