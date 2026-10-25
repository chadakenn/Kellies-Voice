package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FollowUpTakeTest {
    @Test public void claimBeforeTheReaderParksLeavesAGapAndDropsLateAudio() {
        FollowUpTake take = new FollowUpTake(16000);
        byte[] voice = voice();
        assertFalse(take.accept(voice, voice.length, true));
        assertTrue(take.accept(voice, voice.length, true));
        FollowUpTake.Result early = take.claim(0);
        assertFalse(early.workerFinished());
        assertFalse(take.accept(voice, voice.length, true));
        FollowUpTake.Result again = take.claim(0);
        assertEquals(early.audio().length, again.audio().length);
        assertSame(early.capture(), again.capture());
        assertTrue(early.audio().length > 0);
    }

    @Test public void aParkedReaderCanHandOffTheSameFilter() {
        FollowUpTake first = new FollowUpTake(16000);
        FollowUpTake second = new FollowUpTake(16000);
        byte[] voice = voice();
        first.accept(voice, voice.length, true);
        first.accept(voice, voice.length, true);
        first.park();
        FollowUpTake.Result ready = first.claim(50);
        assertTrue(ready.workerFinished());
        assertTrue(ready.audio().length > 0);
        assertFalse(second.accept(voice, voice.length, true));
        assertTrue(second.accept(voice, voice.length, true));
        FollowUpTake.Result other = second.claim(0);
        assertTrue(other.audio().length > 0);
        assertFalse(ready.capture() == other.capture());
    }

    private static byte[] voice() {
        byte[] voice = new byte[320];
        for (int i = 0; i < voice.length; i += 2) {
            voice[i] = 0;
            voice[i + 1] = 0x20;
        }
        return voice;
    }
}
