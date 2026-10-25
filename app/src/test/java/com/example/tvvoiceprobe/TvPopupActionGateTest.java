package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class TvPopupActionGateTest {
    @Test public void atMostOneClickPerPopup() {
        TvPopupActionGate first = new TvPopupActionGate();
        assertTrue(first.claim());
        assertFalse(first.claim());
        TvPopupActionGate next = new TvPopupActionGate();
        assertTrue(next.claim());
    }
}
