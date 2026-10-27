package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanAddressTest {
    @Test public void privateAndLocalNamesMayUseCleartext() {
        assertTrue(LanAddress.cleartextAllowed("192.168.1.20"));
        assertTrue(LanAddress.cleartextAllowed("10.1.2.3"));
        assertTrue(LanAddress.cleartextAllowed("172.16.0.4"));
        assertTrue(LanAddress.cleartextAllowed("127.0.0.1"));
        assertTrue(LanAddress.cleartextAllowed("localhost"));
        assertTrue(LanAddress.cleartextAllowed("homeassistant.local"));
        assertTrue(LanAddress.cleartextAllowed("ha.home"));
        assertTrue(LanAddress.cleartextAllowed("::1"));
    }

    @Test public void publicHostsMustUseHttps() {
        assertFalse(LanAddress.cleartextAllowed("8.8.8.8"));
        assertFalse(LanAddress.cleartextAllowed("1.1.1.1"));
        assertFalse(LanAddress.cleartextAllowed("example.com"));
        assertFalse(LanAddress.cleartextAllowed("ha.ui.nabu.casa"));
        assertFalse(LanAddress.cleartextAllowed(null));
        assertFalse(LanAddress.cleartextAllowed(""));
    }
}
