package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioCaptureResamplingTest {
    @Test public void sixteenKiloHertzIsCopiedIncludingASplitSample() {
        PcmCapture capture = new PcmCapture(PcmCapture.RATE_16K);
        byte[] first = captureBytes(capture, new byte[] {1, 2, 3, 4, 5});
        byte[] second = captureBytes(capture, new byte[] {6, 7, 8});
        assertEquals(4, first.length);
        assertEquals(1, first[0]);
        assertEquals(2, first[1]);
        assertEquals(4, second.length);
        assertEquals(5, second[0] & 255);
        assertEquals(6, second[1]);
    }

    @Test public void fortyEightKiloHertzKeepsOneSampleInThree() {
        PcmCapture capture = new PcmCapture(PcmCapture.RATE_48K);
        byte[] input = new byte[9600];
        for (int i = 0; i < input.length; i += 2) {
            input[i] = (byte) 1000;
            input[i + 1] = (byte) (1000 >> 8);
        }
        byte[] output = captureBytes(capture, input);
        assertEquals(3200, output.length);
        short steady = sampleAt(output, 400);
        assertEquals(1000, steady);
    }

    @Test public void splitReadsMatchOneReadAndAHighToneIsQuieter() {
        byte[] tone = new byte[48 * 2 * 20];
        byte[] voice = new byte[tone.length];
        for (int n = 0; n < tone.length / 2; n++) {
            short high = (short) (Math.sin(2 * Math.PI * 12000.0 / 48000.0 * n) * 12000);
            short low = (short) (Math.sin(2 * Math.PI * 1000.0 / 48000.0 * n) * 12000);
            put(tone, n, high);
            put(voice, n, low);
        }
        byte[] once = captureBytes(new PcmCapture(PcmCapture.RATE_48K), tone);
        PcmCapture split = new PcmCapture(PcmCapture.RATE_48K);
        byte[] a = captureBytes(split, copy(tone, 0, 5));
        byte[] b = captureBytes(split, copy(tone, 5, 7));
        byte[] c = captureBytes(split, copy(tone, 12, tone.length - 12));
        byte[] joined = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, joined, 0, a.length);
        System.arraycopy(b, 0, joined, a.length, b.length);
        System.arraycopy(c, 0, joined, a.length + b.length, c.length);
        assertEquals(once.length, joined.length);
        for (int i = 0; i < once.length; i++) assertEquals(once[i], joined[i]);

        double highPower = power(once);
        double lowPower = power(captureBytes(new PcmCapture(PcmCapture.RATE_48K), voice));
        assertTrue(lowPower > highPower * 8);
    }

    @Test public void otherRatesAreRejectedWithoutProducingAudio() {
        assertTrue(PcmCapture.supported(16000));
        assertTrue(PcmCapture.supported(48000));
        assertFalse(PcmCapture.supported(44100));
        assertFalse(PcmCapture.supported(8000));
    }

    private static byte[] captureBytes(PcmCapture capture, byte[] input) {
        byte[] dst = new byte[input.length + 2];
        int n = capture.process(input, input.length, dst);
        byte[] exact = new byte[n];
        System.arraycopy(dst, 0, exact, 0, n);
        return exact;
    }

    private static byte[] copy(byte[] src, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(src, offset, out, 0, length);
        return out;
    }

    private static void put(byte[] dst, int sample, short value) {
        dst[sample * 2] = (byte) value;
        dst[sample * 2 + 1] = (byte) (value >> 8);
    }

    private static short sampleAt(byte[] pcm, int sample) {
        int p = sample * 2;
        return (short) ((pcm[p] & 255) | (pcm[p + 1] << 8));
    }

    private static double power(byte[] pcm) {
        double sum = 0;
        int count = 0;
        for (int i = 40; i + 1 < pcm.length; i += 2) {
            short value = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
            sum += (double) value * value;
            count++;
        }
        return sum / Math.max(1, count);
    }
}
