package com.example.tvvoiceprobe;

/** JVM-testable speech generation gate. All calls are synchronized. */
final class AnnouncementSpeechSession {
    private int next;
    private int active = -1;

    synchronized int begin() {
        active = ++next;
        return active;
    }

    synchronized boolean isCurrent(int generation) {
        return generation >= 0 && active == generation;
    }

    synchronized boolean finish(int generation) {
        if (!isCurrent(generation)) return false;
        active = -1;
        return true;
    }

    synchronized void cancel() {
        active = -1;
    }
}
