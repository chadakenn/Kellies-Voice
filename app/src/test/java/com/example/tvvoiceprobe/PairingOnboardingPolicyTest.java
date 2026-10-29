package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** No onboarding interception for an existing user or a wake/assistant shortcut. */
public class PairingOnboardingPolicyTest {
    @Test public void onlyAnUnconfiguredOrdinaryAppLaunchShowsSetup() {
        assertTrue(PairingOnboardingPolicy.showOnLaunch(false, false, false));
        assertFalse(PairingOnboardingPolicy.showOnLaunch(true, false, false));
        assertFalse(PairingOnboardingPolicy.showOnLaunch(false, true, false));
        assertFalse(PairingOnboardingPolicy.showOnLaunch(false, false, true));
        assertFalse(PairingOnboardingPolicy.showOnLaunch(true, true, true));
    }

    @Test public void pairingUiCannotBeForcedOverSavedCredentials() {
        for (boolean overlay : new boolean[] {false, true}) {
            for (boolean assist : new boolean[] {false, true}) {
                assertFalse(PairingOnboardingPolicy.showOnLaunch(true, overlay, assist));
            }
        }
    }
}
