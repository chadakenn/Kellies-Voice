package com.example.tvvoiceprobe;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WakeCommandLeadTest {
    @After public void clear() { WakeCommandLead.reset(); }

    @Test public void storesOnlyAudioAfterDetectionAndCapsAtTwoSeconds() {
        assertEquals(0, WakeCommandLead.await(0).length);
        WakeCommandLead.begin();
        short[] first = new short[] {0x0102, -2};
        WakeCommandLead.append(first, first.length);
        short[] extra = new short[WakeCommandLead.MAX_BYTES];
        WakeCommandLead.append(extra, extra.length);
        assertTrue(WakeCommandLead.stopRequested());
        WakeCommandLead.finish();
        assertTrue(WakeCommandLead.isReady());
        byte[] lead = WakeCommandLead.await(50);
        assertEquals(WakeCommandLead.MAX_BYTES, lead.length);
        assertEquals((byte) 0x02, lead[0]);
        assertEquals((byte) 0x01, lead[1]);
        assertEquals(0, WakeCommandLead.await(0).length);
    }

    @Test public void discardIsNotDelivered() {
        WakeCommandLead.begin();
        WakeCommandLead.append(new short[] {7}, 1);
        WakeCommandLead.discard();
        assertFalse(WakeCommandLead.isActive());
        assertEquals(0, WakeCommandLead.await(0).length);
    }
}
