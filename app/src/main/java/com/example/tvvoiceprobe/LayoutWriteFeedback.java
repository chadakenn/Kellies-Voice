package com.example.tvvoiceprobe;

/**
 * Controls the visible result of Home Assistant layout writes.
 *
 * An earlier rejection must not remain on-screen after a later write succeeds.
 * Responses from an older command arriving out of order may not replace a
 * newer result. No entity IDs, layout values, credentials or tokens are stored.
 */
final class LayoutWriteFeedback {
    private int latestResponseId = -1;
    private boolean rejectionVisible;

    synchronized String onResult(int commandId, String setting, boolean success) {
        if (setting == null || commandId < latestResponseId) return null;
        latestResponseId = commandId;

        if (!success) {
            rejectionVisible = true;
            return "Home Assistant didn't accept the "
                    + setting.replace('_', ' ') + " change.";
        }
        if (rejectionVisible) {
            rejectionVisible = false;
            return "Home Assistant accepted the latest layout changes.";
        }
        return null; // Avoid noisy "saved" messages for every background update.
    }

    synchronized void reset() {
        latestResponseId = -1;
        rejectionVisible = false;
    }
}
