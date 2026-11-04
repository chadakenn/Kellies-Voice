package com.example.tvvoiceprobe;

import org.junit.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class RichCardTest {
    @Test public void rejectsEmptyAndCapsRows() {
        assertNull(RichCard.from("list", "T", null, null, null, null, null, 20));
        List<RichCard.Row> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) rows.add(new RichCard.Row("Item " + i, ""));
        RichCard card = RichCard.from("list", "Shopping", "today", rows, null, null, null, 30);
        assertNotNull(card);
        assertEquals(8, card.rows.size());
        assertEquals("list", card.type);
    }

    @Test public void rejectsBadType() {
        List<RichCard.Row> rows = List.of(new RichCard.Row("Milk", ""));
        assertNull(RichCard.from("media", "X", null, rows, null, null, null, 20));
    }
}
