package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TvOverlayLoginPolicyTest {
    @Test public void activeRenewableGrantBlocksLegacyFallback() {
        assertFalse(TvOverlayLoginPolicy.mayUseManual(true));
    }

    @Test public void manualOnlyInstallKeepsExistingOverlayRoute() {
        assertTrue(TvOverlayLoginPolicy.mayUseManual(false));
    }
}
