package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TvHomeIconTest {
    @Test public void commonEntityMdiIconsRenderOffline() {
        assertEquals("💡", TvHomeIcon.glyph("mdi:ceiling-light"));
        assertEquals("💡", TvHomeIcon.glyph("mdi:lightbulb"));
        assertEquals("🌀", TvHomeIcon.glyph("mdi:fan"));
        assertEquals("🎬", TvHomeIcon.glyph("mdi:movie-open"));
        assertEquals("📷", TvHomeIcon.glyph("mdi:cctv"));
        assertEquals("📺", TvHomeIcon.glyph("mdi:television"));
        assertEquals("🏠", TvHomeIcon.glyph("mdi:home"));
        assertEquals("🚗", TvHomeIcon.glyph("mdi:garage"));
        assertEquals("⚙", TvHomeIcon.glyph("mdi:script-text"));
    }

    @Test public void unknownOrUnsafeIconsGetLocalFallback() {
        assertEquals("✦", TvHomeIcon.glyph(null));
        assertEquals("✦", TvHomeIcon.glyph("https://example.com/foo.png"));
        assertEquals("✦", TvHomeIcon.glyph("mdi:../../something"));
        assertEquals("✦", TvHomeIcon.glyph("mdi:" + "x".repeat(70)));
        assertEquals("✦", TvHomeIcon.glyph("mdi:unmapped-custom-icon"));
    }
}
