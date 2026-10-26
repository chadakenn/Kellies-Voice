package com.example.tvvoiceprobe;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TvEventPayloadActionTest {
    @Test public void validatesButtonIdsAndLabels() {
        assertNull(TvEventPayload.action("https://evil", "Go"));
        assertNull(TvEventPayload.action("unlock", "https://example.invalid"));
        assertNull(TvEventPayload.action("bad-id", "Action"));
        assertNotNull(TvEventPayload.action("acknowledge", "Thanks"));
        assertEquals("", TvEventPayload.announcementId("malformed"));
        assertEquals("a".repeat(32), TvEventPayload.announcementId("a".repeat(32)));
    }

    @Test public void deduplicatesAndCapsThree() {
        TvEventPayload.Action a = TvEventPayload.action("ack", "OK");
        List<TvEventPayload.Action> result = TvEventPayload.takeActions(
            new TvEventPayload.Action[]{a,a,TvEventPayload.action("later","Later"),
               TvEventPayload.action("ignore","Ignore"),TvEventPayload.action("fourth","Fourth")});
        assertEquals(3,result.size());
        assertEquals("ack",result.get(0).id);
    }
}
