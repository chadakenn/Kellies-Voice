package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversationLifetimeTest {
    @Test public void anIdleConversationExpiresAfterTenMinutes() {
        long start = 1_000L;
        assertFalse(ConversationLifetime.expired(0L, start + ConversationLifetime.IDLE_MS));
        assertFalse(ConversationLifetime.expired(start, start + ConversationLifetime.IDLE_MS - 1));
        assertTrue(ConversationLifetime.expired(start, start + ConversationLifetime.IDLE_MS));
    }
}
