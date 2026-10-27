package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HaLoginProbeTest {
    @Test public void onlyAuthOkSavesALogin() {
        assertNull(HaLoginProbe.verdict("auth_ok"));
        assertEquals(HaLoginProbe.REJECTED, HaLoginProbe.verdict("auth_invalid"));
        assertEquals("", HaLoginProbe.verdict("auth_required"));
    }
}
