package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class TvAlertConnectionPolicyTest {
    @Test public void explicitResumeAlwaysRefreshesTheAlertConnection() {
        assertTrue(TvAlertConnectionPolicy.shouldForceRestart(true, true, true, false));
        assertTrue(TvAlertConnectionPolicy.shouldForceRestart(true, true, false, false));
        assertTrue(TvAlertConnectionPolicy.shouldForceRestart(true, false, false, false));
        assertFalse(TvAlertConnectionPolicy.shouldForceRestart(false, true, true, false));
    }

    @Test public void normalStartDoesNotInterruptAuthenticationButCanRepairStaleSetup() {
        assertFalse(TvAlertConnectionPolicy.shouldForceRestart(false, true, false, false));
        assertTrue(TvAlertConnectionPolicy.shouldForceRestart(false, true, false, true));
        assertFalse(TvAlertConnectionPolicy.shouldForceRestart(false, false, false, true));
        assertFalse(TvAlertConnectionPolicy.shouldForceRestart(false, true, true, true));
    }

    @Test public void setupWatchdogIgnoresHealthyAndRecentSessions() {
        long begin = 500;
        assertFalse(TvAlertConnectionPolicy.setupTimedOut(begin, begin + 35_000, false));
        assertTrue(TvAlertConnectionPolicy.setupTimedOut(begin, begin + 45_000, false));
        assertFalse(TvAlertConnectionPolicy.setupTimedOut(begin, begin + 90_000, true));
        assertFalse(TvAlertConnectionPolicy.setupTimedOut(-1, 300_000, false));
        assertFalse(TvAlertConnectionPolicy.setupTimedOut(begin, 10, false));
    }

    @Test public void missedPongForcesRecoveryEvenWhenSocketObjectStillExists() {
        assertFalse(TvAlertConnectionPolicy.heartbeatTimedOut(1_000, 30_999));
        assertTrue(TvAlertConnectionPolicy.heartbeatTimedOut(1_000, 31_000));
        assertFalse(TvAlertConnectionPolicy.heartbeatTimedOut(-1, 100_000));
        assertFalse(TvAlertConnectionPolicy.heartbeatTimedOut(30_000, 10_000));
    }

    @Test public void temporaryHaStartupErrorRetriesButRealDenialsStayTerminal() {
        assertTrue(TvAlertConnectionPolicy.retryableSubscriptionError("tv_not_ready"));
        assertFalse(TvAlertConnectionPolicy.retryableSubscriptionError("unauthorized"));
        assertFalse(TvAlertConnectionPolicy.retryableSubscriptionError("not_allowed"));
        assertFalse(TvAlertConnectionPolicy.retryableSubscriptionError("invalid_format"));
        assertFalse(TvAlertConnectionPolicy.retryableSubscriptionError(null));
    }

    @Test public void scopedCommandMissingDuringHaStartupRetriesBeforeLegacy() {
        assertTrue(TvAlertConnectionPolicy.shouldRetryMissingScopedCommand("unknown_command", 0));
        assertTrue(TvAlertConnectionPolicy.shouldRetryMissingScopedCommand("unknown_command", 4));
        assertFalse(TvAlertConnectionPolicy.shouldRetryMissingScopedCommand("unknown_command", 5));
        assertFalse(TvAlertConnectionPolicy.shouldRetryMissingScopedCommand("unauthorized", 0));
        assertFalse(TvAlertConnectionPolicy.shouldRetryMissingScopedCommand("auth_invalid", 0));
        assertFalse(TvAlertConnectionPolicy.shouldRetryMissingScopedCommand("unknown_command", -1));

        // Older HACS supports a single compatibility fallback, not a
        // repeated non-admin legacy subscription that permanently wedges TV alerts.
        assertFalse(TvAlertConnectionPolicy.shouldTryLegacyFallback("unknown_command", 4, false));
        assertTrue(TvAlertConnectionPolicy.shouldTryLegacyFallback("unknown_command", 5, false));
        assertFalse(TvAlertConnectionPolicy.shouldTryLegacyFallback("unknown_command", 5, true));
        assertFalse(TvAlertConnectionPolicy.shouldTryLegacyFallback("unauthorized", 5, false));
        assertFalse(TvAlertConnectionPolicy.shouldTryLegacyFallback("auth_invalid", 5, false));
        assertEquals(2_000L, TvAlertConnectionPolicy.missingScopedRetryDelayMs(0));
        assertEquals(4_000L, TvAlertConnectionPolicy.missingScopedRetryDelayMs(1));
        assertEquals(8_000L, TvAlertConnectionPolicy.missingScopedRetryDelayMs(2));
        assertEquals(16_000L, TvAlertConnectionPolicy.missingScopedRetryDelayMs(3));
        assertEquals(30_000L, TvAlertConnectionPolicy.missingScopedRetryDelayMs(4));
        assertEquals(120_000L, TvAlertConnectionPolicy.missingScopedRetryDelayMs(5));
        assertEquals(120_000L, TvAlertConnectionPolicy.missingScopedRetryDelayMs(999));
        try {
            TvAlertConnectionPolicy.missingScopedRetryDelayMs(-1);
            fail("Negative attempts must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void scopedPermissionDenialGetsFastThenSlowRechecks() {
        assertTrue(TvAlertConnectionPolicy.isScopedPermissionDenial("unauthorized"));
        assertFalse(TvAlertConnectionPolicy.isScopedPermissionDenial("not_allowed"));
        assertFalse(TvAlertConnectionPolicy.isScopedPermissionDenial("auth_invalid"));
        assertFalse(TvAlertConnectionPolicy.isScopedPermissionDenial("invalid_format"));
        assertFalse(TvAlertConnectionPolicy.isScopedPermissionDenial(null));
        assertTrue(TvAlertConnectionPolicy.shouldRetryScopedDenial("unauthorized", 0));
        assertTrue(TvAlertConnectionPolicy.shouldRetryScopedDenial("unauthorized", 4));
        assertFalse(TvAlertConnectionPolicy.shouldRetryScopedDenial("unauthorized", 5));
        assertEquals(2_000L, TvAlertConnectionPolicy.scopedDenialRetryDelayMs(0));
        assertEquals(4_000L, TvAlertConnectionPolicy.scopedDenialRetryDelayMs(1));
        assertEquals(8_000L, TvAlertConnectionPolicy.scopedDenialRetryDelayMs(2));
        assertEquals(16_000L, TvAlertConnectionPolicy.scopedDenialRetryDelayMs(3));
        assertEquals(30_000L, TvAlertConnectionPolicy.scopedDenialRetryDelayMs(4));
        assertEquals(120_000L, TvAlertConnectionPolicy.scopedDenialRetryDelayMs(5));
        assertEquals(120_000L, TvAlertConnectionPolicy.scopedDenialRetryDelayMs(1000));
        try {
            TvAlertConnectionPolicy.scopedDenialRetryDelayMs(-1);
            fail("Negative attempts must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void independentMonitorOnlyRepairsMissingOrOverdueRetries() {
        assertTrue(TvAlertConnectionPolicy.shouldMonitorReconnect(
                true, true, true, false, false, -1L, 50_000L));
        assertFalse(TvAlertConnectionPolicy.shouldMonitorReconnect(
                true, true, true, false, false, 100_000L, 104_999L));
        assertTrue(TvAlertConnectionPolicy.shouldMonitorReconnect(
                true, true, true, false, false, 100_000L, 105_000L));
        assertFalse(TvAlertConnectionPolicy.shouldMonitorReconnect(
                false, true, true, false, false, -1L, 50_000L));
        assertFalse(TvAlertConnectionPolicy.shouldMonitorReconnect(
                true, false, true, false, false, -1L, 50_000L));
        assertFalse(TvAlertConnectionPolicy.shouldMonitorReconnect(
                true, true, false, false, false, -1L, 50_000L));
        assertFalse(TvAlertConnectionPolicy.shouldMonitorReconnect(
                true, true, true, true, false, -1L, 50_000L));
        assertFalse(TvAlertConnectionPolicy.shouldMonitorReconnect(
                true, true, true, false, true, -1L, 50_000L));
    }

    @Test public void reconnectBackoffIsBounded() {
        assertEquals(2_000, TvAlertConnectionPolicy.retryDelayMs(0));
        assertEquals(4_000, TvAlertConnectionPolicy.retryDelayMs(1));
        assertEquals(16_000, TvAlertConnectionPolicy.retryDelayMs(3));
        assertEquals(30_000, TvAlertConnectionPolicy.retryDelayMs(9));
        assertEquals(2_000, TvAlertConnectionPolicy.retryDelayMs(-5));
    }
}
