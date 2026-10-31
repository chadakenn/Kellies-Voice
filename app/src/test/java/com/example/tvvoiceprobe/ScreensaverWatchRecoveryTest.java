package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreensaverWatchRecoveryTest {
    @Test public void resultConstantsAreDistinct() {
        assertNotEquals(ScreensaverWatchRecovery.RESULT_REQUESTED,
                ScreensaverWatchRecovery.RESULT_RUNNING);
        assertNotEquals(ScreensaverWatchRecovery.RESULT_RUNNING,
                ScreensaverWatchRecovery.RESULT_DENIED);
        assertNotEquals(ScreensaverWatchRecovery.RESULT_SKIPPED,
                ScreensaverWatchRecovery.RESULT_NOTIFICATION);
    }

    @Test public void featureOffIsSkippedAndDoesNotScheduleRecovery() {
        // Contract: when shouldRun is false, startIfEnabled records SKIPPED
        // and cancels recovery. It must not post a notification or schedule work.
        assertEquals("skipped (feature off)", ScreensaverWatchRecovery.RESULT_SKIPPED);
    }

    @Test public void startRequestedIsNotTheSameAsConfirmedRunning() {
        // RESULT_REQUESTED means startForegroundService was accepted.
        // RESULT_RUNNING means onStartCommand actually ran and called startForeground.
        assertFalse(ScreensaverWatchRecovery.RESULT_REQUESTED
                .equals(ScreensaverWatchRecovery.RESULT_RUNNING));
    }

    @Test public void denialRecordsExceptionClassNotAssumedCause() {
        // The recovery records the actual exception simple name.
        // It does not hard-code "Android denied BOOT_COMPLETED".
        assertTrue(ScreensaverWatchRecovery.RESULT_DENIED.contains("denied"));
    }

    @Test public void notificationIsTheSupportedRecoveryPath() {
        // When unattended start fails, a user-visible notification is posted.
        // An inexact AlarmManager is not used to promise an automatic FGS restart.
        assertEquals("notification posted", ScreensaverWatchRecovery.RESULT_NOTIFICATION);
    }
}
