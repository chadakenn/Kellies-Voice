package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TvReplayEventTest {
    @Test public void onlyOpaqueFrigateIdsAreAllowed() {
        assertTrue(TvReplayEvent.validId("1730999999.123456-a9b8c7"));
        for (String bad : new String[]{"", "..", "../etc/passwd",
                "https://host/api/events/123", "//evil.invalid",
                "%2e%2e", "abc?token=x", "abc#x", "abc\\folder",
                "a", "abc\n123", "abc..def", "foo/bar"}) {
            assertFalse(bad, TvReplayEvent.validId(bad));
        }
        assertFalse(TvReplayEvent.validId(null));
    }

    @Test public void labelsCannotContainUrisOrPayloadMarkup() {
        assertTrue(TvReplayEvent.validLabel("person"));
        assertTrue(TvReplayEvent.validLabel("delivery_package"));
        for (String bad : new String[]{"", "Person", "a/b", "https://evil",
                "<script>", "person\nnews", "car?token=secret",
                "a".repeat(41)}) {
            assertFalse(bad, TvReplayEvent.validLabel(bad));
        }
        assertFalse(TvReplayEvent.validLabel(null));
    }

    @Test public void epochSecondsCannotOverflowDate() {
        assertEquals(1730999999000L, TvReplayEvent.epochMillis(1730999999));
        assertEquals(-1L, TvReplayEvent.epochMillis(0d));
        assertEquals(-1L, TvReplayEvent.epochMillis(Double.POSITIVE_INFINITY));
        assertEquals(-1L, TvReplayEvent.epochMillis(Double.NaN));
        assertEquals(-1L, TvReplayEvent.epochMillis(Double.MAX_VALUE));
        assertEquals(-1L, TvReplayEvent.epochMillis(-1));
    }
}
