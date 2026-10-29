package com.example.tvvoiceprobe;

/**
 * Routes only an unconfigured, ordinary TV launch to the visible setup UI.
 *
 * This decision never enables the microphone, changes a saved credential,
 * advertises mDNS, or opens a LAN listener. Those require an explicit
 * physical remote action inside PairingSetupActivity.
 */
public final class PairingOnboardingPolicy {
    private PairingOnboardingPolicy() {}

    public static boolean showOnLaunch(boolean hasSavedLogin,
            boolean launcherOverlayShortcut, boolean assistantInvocation) {
        return !hasSavedLogin && !launcherOverlayShortcut && !assistantInvocation;
    }
}
