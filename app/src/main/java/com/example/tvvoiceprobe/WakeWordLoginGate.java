package com.example.tvvoiceprobe;

import android.content.Context;

/**
 * One shared, token-free login eligibility check for both wake-word Settings
 * entry points. A selected owner-approved renewable login must not fall back
 * to an older manual credential if the grant is missing or revoked.
 */
public final class WakeWordLoginGate {
    private WakeWordLoginGate() { }

    static boolean eligible(boolean renewableSelected, boolean renewableActive,
                            boolean manualCredentialsStored) {
        return renewableSelected ? renewableActive : manualCredentialsStored;
    }

    public static boolean hasSavedLogin(Context context) {
        TvRenewableLoginVault vault = new TvRenewableLoginVault(context);
        // Never read the legacy token if renewable mode has been selected.
        if (vault.blocksManualFallback()) {
            return eligible(true, vault.hasActiveLogin(), false);
        }
        boolean manualStored = false;
        try {
            manualStored = new CredentialStore(context).load() != null;
        } catch (Exception ignored) {
            // A corrupt/manual credential is not a valid login.
        }
        return eligible(false, false, manualStored);
    }
}
