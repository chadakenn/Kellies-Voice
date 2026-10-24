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

    @Test public void reconnectBackoffIsBounded() {
        assertEquals(2_000, TvAlertConnectionPolicy.retryDelayMs(0));
        assertEquals(4_000, TvAlertConnectionPolicy.retryDelayMs(1));
        assertEquals(16_000, TvAlertConnectionPolicy.retryDelayMs(3));
        assertEquals(30_000, TvAlertConnectionPolicy.retryDelayMs(9));
        assertEquals(2_000, TvAlertConnectionPolicy.retryDelayMs(-5));
    }
}
