package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class AssistPipelineChoiceTest {
    @Test public void defaultAlwaysFollowsPreferredHaPipeline() {
        assertEquals("", AssistPipelineChoice.normalize(null));
        assertEquals("", AssistPipelineChoice.normalize(""));
        assertEquals("", AssistPipelineChoice.normalize(" "));
        assertEquals("conversation.home_assistant",
                AssistPipelineChoice.normalize("conversation.home_assistant"));
        assertEquals("7f174e72-9a10-4fdc-baad-24ae7510bca3",
                AssistPipelineChoice.normalize("7f174e72-9a10-4fdc-baad-24ae7510bca3"));
    }

    @Test public void refusesUnsafeOrOversizedIds() {
        assertEquals("", AssistPipelineChoice.normalize("test\nother"));
        assertEquals("", AssistPipelineChoice.normalize("test\u0000other"));
        assertEquals("", AssistPipelineChoice.normalize("a".repeat(129)));
        assertEquals("", AssistPipelineChoice.normalize("こんにちは"));
        assertEquals("my-pipeline", AssistPipelineChoice.normalize("my-pipeline"));
    }

    @Test public void labelsRemainReasonablyShortAndReadable() {
        AssistPipelineChoice.Option option =
                new AssistPipelineChoice.Option("abc-123", "Kitchen voice pipeline");
        assertEquals("Kitchen voice pipeline (abc-123)", option.label());
        assertEquals("abc-123", new AssistPipelineChoice.Option("abc-123", "").label());
        assertEquals(70, new AssistPipelineChoice.Option("abc-123", "x".repeat(100)).name.length());
    }
}
