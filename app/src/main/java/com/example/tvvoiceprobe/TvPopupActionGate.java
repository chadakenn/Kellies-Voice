package com.example.tvvoiceprobe;

/** Each displayed popup may send one and only one explicit remote button. */
public final class TvPopupActionGate {
    private boolean consumed;
    public boolean claim() {
        if (consumed) return false;
        consumed = true;
        return true;
    }
}
