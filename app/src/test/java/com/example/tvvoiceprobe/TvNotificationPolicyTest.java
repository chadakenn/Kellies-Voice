package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TvNotificationPolicyTest {
    @Test public void explicitPriorityAllowlistAndLegacyDefault() {
        assertEquals("normal", TvNotificationPolicy.priority(null));
        assertEquals("low", TvNotificationPolicy.priority("low"));
        assertEquals("high", TvNotificationPolicy.priority("high"));
        assertNull(TvNotificationPolicy.priority("critical"));
        assertNull(TvNotificationPolicy.priority("HIGH"));
        assertNull(TvNotificationPolicy.priority("../oops"));
    }

    @Test public void normalAndOvernightQuietWindows() {
        assertTrue(TvNotificationPolicy.withinQuietHours(23*60, 22*60, 7*60));
        assertTrue(TvNotificationPolicy.withinQuietHours(6*60+59, 22*60, 7*60));
        assertFalse(TvNotificationPolicy.withinQuietHours(7*60, 22*60, 7*60));
        assertFalse(TvNotificationPolicy.withinQuietHours(20*60, 22*60, 7*60));
        assertTrue(TvNotificationPolicy.withinQuietHours(8*60, 7*60, 22*60));
        assertFalse(TvNotificationPolicy.withinQuietHours(22*60, 7*60, 22*60));
        assertFalse(TvNotificationPolicy.withinQuietHours(100, 100, 100));
        assertFalse(TvNotificationPolicy.withinQuietHours(-1, 100, 300));
    }

    @Test public void optInQuietAndUrgentBehavior() {
        assertTrue(TvNotificationPolicy.shouldDisplay("normal", false, 23*60, 22*60, 7*60));
        assertFalse(TvNotificationPolicy.shouldDisplay("normal", true, 23*60, 22*60, 7*60));
        assertFalse(TvNotificationPolicy.shouldDisplay("low", true, 2*60, 22*60, 7*60));
        assertTrue(TvNotificationPolicy.shouldDisplay("high", true, 2*60, 22*60, 7*60));
        assertFalse(TvNotificationPolicy.shouldDisplay("INVALID", false, 0, 0, 0));
        assertFalse(TvNotificationPolicy.shouldSpeak("low"));
        assertTrue(TvNotificationPolicy.shouldSpeak("normal"));
    }

    @Test public void priorityChangesOnlyExplicitSizeOverrides() {
        assertEquals(TvAlertLayout.SMALL,
                TvNotificationPolicy.alertSize("low", TvAlertLayout.LARGE));
        assertEquals(TvAlertLayout.LARGE,
                TvNotificationPolicy.alertSize("high", TvAlertLayout.SMALL));
        assertEquals(TvAlertLayout.MEDIUM,
                TvNotificationPolicy.alertSize("normal", TvAlertLayout.MEDIUM));
    }
}
