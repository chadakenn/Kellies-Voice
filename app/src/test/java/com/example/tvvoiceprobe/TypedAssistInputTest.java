package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TypedAssistInputTest {
    @Test public void normalizesUserRequestWithoutAddingCommands() {
        assertEquals("Turn on the living room lights",
                TypedAssistInput.clean("  Turn on the living room lights  "));
        assertEquals("What's the weather?", TypedAssistInput.clean("What's the weather?"));
    }

    @Test public void rejectsEmptyOversizedOrControlInput() {
        assertEquals("", TypedAssistInput.clean(null));
        assertEquals("", TypedAssistInput.clean("   "));
        assertEquals("", TypedAssistInput.clean("a".repeat(TypedAssistInput.MAX_LENGTH + 1)));
        assertEquals("", TypedAssistInput.clean("open\nmy door"));
        assertEquals("", TypedAssistInput.clean("hello\u0000world"));
        assertEquals("x".repeat(TypedAssistInput.MAX_LENGTH),
                TypedAssistInput.clean("x".repeat(TypedAssistInput.MAX_LENGTH)));
    }
}
