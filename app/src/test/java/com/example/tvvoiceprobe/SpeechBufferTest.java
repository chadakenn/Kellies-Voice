package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpeechBufferTest {
    @Test public void silenceIsNotATurn() {
        SpeechBuffer buffer = new SpeechBuffer(16000);
        byte[] quiet = new byte[3200];
        assertFalse(buffer.add(quiet, quiet.length, false));
        assertEquals(0, buffer.to16k().length);
    }

    @Test public void prerollAndDetectedSpeechAreKept() {
        SpeechBuffer buffer = new SpeechBuffer(16000);
        byte[] lead = new byte[] {1, 0, 1, 0};
        byte[] voice = voiceChunk(320);
        buffer.add(lead, lead.length, false);
        assertFalse(buffer.latched());
        buffer.add(voice, voice.length, true);
        buffer.add(voice, voice.length, true);
        assertTrue(buffer.latched());
        byte[] claimed = buffer.to16k();
        assertTrue(claimed.length >= lead.length + voice.length);
        assertEquals(1, claimed[0]);
    }

    @Test public void anOddByteIsKeptForTheNextRead() {
        SpeechBuffer buffer = new SpeechBuffer(16000);
        byte[] first = new byte[] {1, 2, 3};
        byte[] second = new byte[] {4, 5, 6};
        assertFalse(buffer.add(first, first.length, true));
        assertTrue(buffer.add(second, second.length, true));
        byte[] claimed = buffer.to16k();
        assertEquals(6, claimed.length);
        assertEquals(3, claimed[2] & 255);
        assertEquals(4, claimed[3]);
    }

    private static byte[] voiceChunk(int length) {
        byte[] voice = new byte[length];
        for (int i = 0; i < voice.length; i += 2) {
            voice[i] = 0;
            voice[i + 1] = 0x20;
        }
        return voice;
    }
}
