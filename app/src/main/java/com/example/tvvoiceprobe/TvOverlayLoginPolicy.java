package com.example.tvvoiceprobe;

/** Selects the voice overlay's login path without downgrading from an
 * owner-approved renewable TV account to legacy manual credentials.
 */
final class TvOverlayLoginPolicy {
    private TvOverlayLoginPolicy() { }

    /** Manual credentials are eligible only when no active renewable TV grant
     * exists. A failed refresh must never silently authorize the old account.
     */
    static boolean mayUseManual(boolean renewableActive) {
        return !renewableActive;
    }
}
