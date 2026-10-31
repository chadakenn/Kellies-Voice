package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommunityThemeStoreTest {
    private static final String VALID = """
        {
          "format": 1,
          "id": "community.copper-night",
          "name": "Copper Night",
          "author": "Test",
          "base": "glass",
          "colors": {
            "screen": "#1B1412",
            "panel": "#2B1B16",
            "card": "#493128",
            "cardFocused": "#62453B",
            "accent": "#E9B390",
            "accentSecondary": "#BA785B",
            "text": "#F7E8DB",
            "textMuted": "#C59A80",
            "stroke": "#B98465",
            "focus": "#E9B390",
            "success": "#4CAF50",
            "warning": "#FF9800"
          },
          "shapes": {
            "panelRadiusDp": 16,
            "cardRadiusDp": 12,
            "focusBorderDp": 3,
            "spacingDp": 12
          },
          "animationScale": 1.0
        }
        """;

    @Test public void parseValidTheme() {
        CommunityThemeStore.Theme theme = CommunityThemeStore.parse(VALID);
        assertEquals("community.copper-night", theme.id);
        assertEquals("Copper Night", theme.name);
        assertEquals(16, theme.panelRadiusDp);
        assertEquals(1.0f, theme.animationScale, 0.01f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectBadId() {
        CommunityThemeStore.parse(VALID.replace("community.copper-night", "bad-id"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectBadColor() {
        CommunityThemeStore.parse(VALID.replace("#F7E8DB", "red"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectLowContrast() {
        String low = VALID.replace("#F7E8DB", "#1B1412");
        CommunityThemeStore.parse(low);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectUnknownKey() {
        CommunityThemeStore.parse(VALID.replace("\"format\": 1", "\"format\": 1, \"script\": \"evil\""));
    }

    @Test public void isId() {
        assertTrue(CommunityThemeStore.isId("community.copper-night"));
        assertFalse(CommunityThemeStore.isId("glass"));
        assertFalse(CommunityThemeStore.isId("community."));
        assertFalse(CommunityThemeStore.isId(null));
    }
}
