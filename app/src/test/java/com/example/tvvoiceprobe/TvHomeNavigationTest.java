package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Remote navigation must reach every visible action in the two-row TV grid. */
public class TvHomeNavigationTest {
    @Test public void noShortcutsKeepsOriginalThreeButtonRow() {
        assertEquals(0, TvHomeNavigation.up(0, 0));
        assertEquals(0, TvHomeNavigation.left(0, 0));
        assertEquals(1, TvHomeNavigation.right(0, 0));
        assertEquals(2, TvHomeNavigation.right(2, 0));
        assertEquals(2, TvHomeNavigation.down(2, 0));
    }

    @Test public void oneOrTwoActionsCanBeReachedAndReturnToToolbar() {
        assertEquals(3, TvHomeNavigation.up(0, 1));
        assertEquals(3, TvHomeNavigation.up(2, 1));
        assertEquals(3, TvHomeNavigation.right(3, 1));
        assertEquals(0, TvHomeNavigation.down(3, 1));

        assertEquals(3, TvHomeNavigation.up(0, 2));
        assertEquals(4, TvHomeNavigation.up(1, 2));
        assertEquals(4, TvHomeNavigation.up(2, 2));
        assertEquals(4, TvHomeNavigation.right(3, 2));
        assertEquals(4, TvHomeNavigation.right(4, 2));
        assertEquals(3, TvHomeNavigation.left(4, 2));
        assertEquals(0, TvHomeNavigation.down(3, 2));
        assertEquals(2, TvHomeNavigation.down(4, 2));
    }

    @Test public void thirdFullWidthActionCanNeverBeSkipped() {
        // Visible layout: [3][4] above [  5  ] above [0][1][2].
        for (int start = 0; start < 3; start++) {
            assertEquals(5, TvHomeNavigation.up(start, 3));
        }
        assertEquals(3, TvHomeNavigation.up(5, 3));
        assertEquals(4, TvHomeNavigation.right(3, 3));
        assertEquals(5, TvHomeNavigation.down(4, 3));
        assertEquals(5, TvHomeNavigation.down(3, 3));
        assertEquals(1, TvHomeNavigation.down(5, 3));
        assertEquals(5, TvHomeNavigation.right(5, 3));
        assertEquals(5, TvHomeNavigation.left(5, 3));
        assertEquals(4, TvHomeNavigation.right(4, 3));
    }

    @Test public void fourActionsUseTwoRowsAndStayWithinBounds() {
        // Visible layout: [3][4] above [5][6] above [0][1][2].
        assertEquals(5, TvHomeNavigation.up(0, 4));
        assertEquals(5, TvHomeNavigation.up(1, 4));
        assertEquals(6, TvHomeNavigation.up(2, 4));
        assertEquals(3, TvHomeNavigation.up(5, 4));
        assertEquals(4, TvHomeNavigation.up(6, 4));
        assertEquals(5, TvHomeNavigation.down(3, 4));
        assertEquals(6, TvHomeNavigation.down(4, 4));
        assertEquals(0, TvHomeNavigation.down(5, 4));
        assertEquals(2, TvHomeNavigation.down(6, 4));
        assertEquals(4, TvHomeNavigation.right(3, 4));
        assertEquals(4, TvHomeNavigation.right(4, 4));
        assertEquals(6, TvHomeNavigation.right(5, 4));
        assertEquals(6, TvHomeNavigation.right(6, 4));
        assertEquals(5, TvHomeNavigation.left(6, 4));
        assertEquals(5, TvHomeNavigation.left(5, 4));
    }

    @Test public void shortcutCountIsClampedToFour() {
        assertEquals(6, TvHomeNavigation.up(2, 99));
        assertEquals(0, TvHomeNavigation.down(5, 99));
        assertEquals(2, TvHomeNavigation.down(6, 99));
    }
}
