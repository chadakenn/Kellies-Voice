package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayPreferencesLabelsTest {
    @Test
    public void positionLabelsMatchConstants() {
        assertEquals("Top left", OverlayPreferences.positionLabel(OverlayPreferences.POSITION_TOP_LEFT));
        assertEquals("Top right", OverlayPreferences.positionLabel(OverlayPreferences.POSITION_TOP_RIGHT));
        assertEquals("Bottom left", OverlayPreferences.positionLabel(OverlayPreferences.POSITION_BOTTOM_LEFT));
        assertEquals("Bottom right", OverlayPreferences.positionLabel(OverlayPreferences.POSITION_BOTTOM_RIGHT));
        assertEquals("Center", OverlayPreferences.positionLabel(OverlayPreferences.POSITION_CENTER));
        assertEquals("Bottom center", OverlayPreferences.positionLabel(OverlayPreferences.POSITION_BOTTOM_CENTER));
    }

    @Test
    public void sizeLabelsMatchConstants() {
        assertEquals("Small", OverlayPreferences.sizeLabel(OverlayPreferences.SIZE_SMALL));
        assertEquals("Medium", OverlayPreferences.sizeLabel(OverlayPreferences.SIZE_MEDIUM));
        assertEquals("Large", OverlayPreferences.sizeLabel(OverlayPreferences.SIZE_LARGE));
    }

    @Test
    public void hideLabelNeverWhenZero() {
        assertTrue(OverlayPreferences.POSITION_DEFAULT.equals("default"));
    }
}
