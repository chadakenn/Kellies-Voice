package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class SkinStoreTest {
    @Test
    public void skinCatalogMapsToExistingOverlayPreferencesIds() {
        // Verify Kotlin object interop and mapping to validated OverlayPreferences keys.
        assertEquals(OverlayPreferences.SKIN_GLASS,
                com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getCOPPER().getId());
        assertEquals(OverlayPreferences.SKIN_CLASSIC,
                com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getMINIMAL().getId());
        assertEquals(OverlayPreferences.SKIN_AURORA,
                com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getAURORA().getId());
        assertEquals(OverlayPreferences.SKIN_SIGNAL,
                com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getNIGHT().getId());
        assertEquals(OverlayPreferences.SKIN_OBSIDIAN,
                com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getOBSIDIAN().getId());
        assertEquals(5, com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getALL().size());
        // Unknown id falls back to copper/glass
        assertEquals(OverlayPreferences.SKIN_GLASS,
                com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.byId("unknown").getId());
    }

    @Test
    public void skinIdsMatchValidatedConstants() {
        // Ensures Compose skins stay compatible with OverlayPreferences persistence.
        assertEquals("glass", com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getCOPPER().getId());
        assertEquals("classic", com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getMINIMAL().getId());
        assertEquals("aurora", com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getAURORA().getId());
        assertEquals("signal", com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getNIGHT().getId());
        assertEquals("obsidian", com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getOBSIDIAN().getId());
        assertEquals("Neon Obsidian",
                com.example.tvvoiceprobe.ui.theme.KellieSkins.INSTANCE.getOBSIDIAN().getDisplayName());
    }
}
