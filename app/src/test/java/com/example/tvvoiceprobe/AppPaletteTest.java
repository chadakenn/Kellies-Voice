package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AppPaletteTest {
    @Test public void unknownAndDefaultSkinPreserveCopperColors() {
        AppPalette copper = AppPalette.forSkin(OverlayPreferences.SKIN_GLASS);
        AppPalette unknown = AppPalette.forSkin("not-a-theme");
        assertEquals(0xff1B1412, copper.screen);
        assertEquals(0xffE9B390, copper.accent);
        assertEquals(0xff62453B, copper.button);
        assertEquals(copper.screen, unknown.screen);
        assertEquals(copper.accent, AppPalette.forSkin(null).accent);
    }

    @Test public void eachSupportedSkinHasDistinctMainAndMenuColors() {
        AppPalette copper = AppPalette.forSkin(OverlayPreferences.SKIN_GLASS);
        AppPalette classic = AppPalette.forSkin(OverlayPreferences.SKIN_CLASSIC);
        AppPalette night = AppPalette.forSkin(OverlayPreferences.SKIN_SIGNAL);
        AppPalette aurora = AppPalette.forSkin(OverlayPreferences.SKIN_AURORA);

        assertNotEquals(copper.screen, classic.screen);
        assertNotEquals(classic.screen, night.screen);
        assertNotEquals(night.screen, aurora.screen);
        assertNotEquals(copper.focus, aurora.focus);
        assertNotEquals(copper.menuStart, aurora.menuStart);
        assertNotEquals(copper.menuFocus, aurora.menuFocus);
        assertNotEquals(night.accent, aurora.accent);
    }

    @Test public void textAndButtonsRemainOpaqueForTvReadability() {
        for (String skin : new String[] {
                OverlayPreferences.SKIN_GLASS,
                OverlayPreferences.SKIN_CLASSIC,
                OverlayPreferences.SKIN_SIGNAL,
                OverlayPreferences.SKIN_AURORA
        }) {
            AppPalette palette = AppPalette.forSkin(skin);
            assertEquals(0xff000000, palette.text & 0xff000000);
            assertEquals(0xff000000, palette.focus & 0xff000000);
            assertEquals(0xff000000, palette.menuButton & 0xff000000);
            assertTrue(palette.text != palette.screen);
            assertTrue(palette.menuFocus != palette.menuButton);
        }
    }
}
