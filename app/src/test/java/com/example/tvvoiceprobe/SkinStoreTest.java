package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class SkinStoreTest {
    @Test
    public void defaultSkinIsCopper() {
        // SkinStore requires Context; this verifies the skin catalog and fallback.
        assertEquals("copper", com.example.tvvoiceprobe.ui.theme.KellieSkins.COPPER.getId());
        assertEquals(4, com.example.tvvoiceprobe.ui.theme.KellieSkins.ALL.size());
        assertEquals("Copper", com.example.tvvoiceprobe.ui.theme.KellieSkins.byId("unknown").getDisplayName());
    }
}
