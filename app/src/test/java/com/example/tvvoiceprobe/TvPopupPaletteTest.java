package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.*;

/** UI presentation regression: all event popups honor the same skin as Compose. */
public class TvPopupPaletteTest {
    @Test public void copperKeepsExistingAnnouncementAppearance() {
        TvPopupPalette p = TvPopupPalette.forSkin(OverlayPreferences.SKIN_GLASS);
        assertEquals(0xf51b1512, p.backgroundStart);
        assertEquals(0xf52b1d17, p.backgroundEnd);
        assertEquals(0xffad835d, p.border);
        assertEquals(0xffe1b27b, p.rule);
        assertEquals(0xffeac296, p.title);
        assertEquals(0xfffae8d5, p.body);
        assertEquals(0xff2d211c, p.letterbox);
        assertEquals(12, p.cornerRadiusDp);
        assertEquals(0xf514100e, p.forecastStart);
        assertEquals(0xffc4884a, p.forecastBorder);
        assertFalse(p.auroraGlass);
    }

    @Test public void neonObsidianUsesComposeColorsInsteadOfCopper() {
        AppPalette shared = AppPalette.forSkin(OverlayPreferences.SKIN_OBSIDIAN);
        TvPopupPalette p = TvPopupPalette.forSkin(OverlayPreferences.SKIN_OBSIDIAN);
        assertEquals(shared.accent, p.title);
        assertEquals(0xff00e5ff, p.title);
        assertEquals(shared.text, p.body);
        assertEquals(shared.screen, p.letterbox);
        assertEquals(shared.focus, p.rule);
        assertEquals(shared.accent, p.border);
        assertEquals(shared.accent, p.forecastTitle);
        assertEquals(shared.text, p.forecastText);
        assertEquals(18, p.cornerRadiusDp);
        assertFalse(p.auroraGlass);
        assertNotEquals(TvPopupPalette.forSkin(OverlayPreferences.SKIN_GLASS).title, p.title);
    }

    @Test public void everyCatalogSkinHasMatchingReadableAlertColors() {
        String[] skins = {
                OverlayPreferences.SKIN_GLASS,
                OverlayPreferences.SKIN_CLASSIC,
                OverlayPreferences.SKIN_SIGNAL,
                OverlayPreferences.SKIN_AURORA,
                OverlayPreferences.SKIN_OBSIDIAN
        };
        for (String skin : skins) {
            TvPopupPalette p = TvPopupPalette.forSkin(skin);
            assertNotEquals("No transparent title for " + skin, 0, p.title >>> 24);
            assertNotEquals("No transparent text for " + skin, 0, p.body >>> 24);
            assertEquals(0xf5, p.backgroundStart >>> 24);
            assertTrue(p.cornerRadiusDp > 0);
        }
    }

    @Test public void auroraRetainsGlassForecastAndGetsGlassAlerts() {
        TvPopupPalette p = TvPopupPalette.forSkin(OverlayPreferences.SKIN_AURORA);
        assertTrue(p.auroraGlass);
        assertEquals(0xffedf3ff, p.forecastTitle);
        assertEquals(0xfff2f5ff, p.forecastText);
        assertEquals(AppPalette.forSkin(OverlayPreferences.SKIN_AURORA).accent, p.title);
    }

    @Test public void invalidSkinFallsBackToCopperSafely() {
        assertEquals(TvPopupPalette.forSkin(OverlayPreferences.SKIN_GLASS).body,
                TvPopupPalette.forSkin("not-a-theme").body);
    }
}
