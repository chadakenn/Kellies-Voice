package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RegistryDeviceIdTest {
    @Test public void aRegistryIdIsKeptAndAPairingUuidIsNot() {
        assertEquals("aabbccddeeff00112233445566778899",
                RegistryDeviceId.accepted(" aabbccddeeff00112233445566778899 "));
        assertEquals("", RegistryDeviceId.accepted("6f9619ff-8b86-d011-b42d-00c04fc964ff"));
        assertEquals("", RegistryDeviceId.accepted(""));
        assertEquals("", RegistryDeviceId.accepted(null));
        assertEquals("", RegistryDeviceId.accepted("short"));
    }
}
