package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** No Android services or Home Assistant connection should be needed for the handoff. */
public class ComposeSettingsInteropTest {
    @Test public void queuesOnlyKnownLayoutKeysAndPreservesOrder() {
        ComposeSettingsInterop.takePendingLayoutUpdates();
        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.POSITION);
        ComposeSettingsInterop.queueLayoutUpdate("not_a_layout_key");
        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.WEATHER_DURATION);
        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.POSITION);
        assertArrayEquals(new String[] {HaLayout.POSITION, HaLayout.WEATHER_DURATION},
                ComposeSettingsInterop.takePendingLayoutUpdates());
        assertEquals(0, ComposeSettingsInterop.takePendingLayoutUpdates().length);
    }

    @Test public void updateChannelInvalidationIsOneShotAndDoesNotStartAnything() {
        ComposeSettingsInterop.takeUpdateChannelChanged();
        assertFalse(ComposeSettingsInterop.takeUpdateChannelChanged());
        ComposeSettingsInterop.notifyUpdateChannelChanged();
        ComposeSettingsInterop.notifyUpdateChannelChanged();
        assertTrue(ComposeSettingsInterop.takeUpdateChannelChanged());
        assertFalse(ComposeSettingsInterop.takeUpdateChannelChanged());
        assertEquals(UpdateChannel.STABLE, UpdateChannel.normalize(null));
        assertEquals(UpdateChannel.DEVELOPMENT, UpdateChannel.normalize(UpdateChannel.DEVELOPMENT));
        assertFalse(UpdateChannel.allowsAutomaticCheck(UpdateChannel.DEVELOPMENT));
    }

    @Test public void handlesNullAndUnrelatedSettingsWithoutSideEffects() {
        ComposeSettingsInterop.takePendingLayoutUpdates();
        ComposeSettingsInterop.queueLayoutUpdate(null);
        ComposeSettingsInterop.queueLayoutUpdate("");
        ComposeSettingsInterop.queueLayoutUpdate("ha_token");
        assertArrayEquals(new String[0], ComposeSettingsInterop.takePendingLayoutUpdates());
    }
}
