package com.example.tvvoiceprobe;

/** Selects the voice overlay's login path without downgrading from an
 * owner-approved renewable TV account to legacy manual credentials.
 */
final class TvOverlayLoginPolicy {
    private TvOverlayLoginPolicy() { }

    /** Callers pass persistent renewable-mode selection, not merely whether a
     * currently readable active token exists. Failure or revoke stays blocked.
     */
    static boolean mayUseManual(boolean renewableModeSelected) {
        return !renewableModeSelected;
    }
}
