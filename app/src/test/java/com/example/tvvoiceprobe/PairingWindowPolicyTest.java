package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.*;

/** Security tests for the intentional, read-only discovery stage of #193. */
public class PairingWindowPolicyTest {
    @Test public void boundedWindowClosesAtExpiryAndRequestLimit() {
        long begin = 200L;
        assertTrue(PairingWindowPolicy.active(begin, begin, 0));
        assertTrue(PairingWindowPolicy.active(begin, begin + 119999L, 31));
        assertFalse(PairingWindowPolicy.active(begin, begin + 120000L, 0));
        assertFalse(PairingWindowPolicy.active(begin, begin - 1, 0));
        assertFalse(PairingWindowPolicy.active(begin, begin + 1, 32));
        assertFalse(PairingWindowPolicy.active(-1, begin, 0));
        assertFalse(PairingWindowPolicy.active(begin, begin, -1));
    }

    @Test public void exactReadOnlyCapabilityRequestIsTheOnlyAllowedRoute() {
        assertTrue(PairingWindowPolicy.permittedRequest(
                "GET /v1/pair/capabilities HTTP/1.1"));
        assertTrue(PairingWindowPolicy.permittedRequest(
                "GET /v1/pair/capabilities HTTP/1.0"));
        assertFalse(PairingWindowPolicy.permittedRequest(
                "POST /v1/pair/capabilities HTTP/1.1"));
        assertFalse(PairingWindowPolicy.permittedRequest(
                "GET /v1/pair/capabilities?token=a HTTP/1.1"));
        assertFalse(PairingWindowPolicy.permittedRequest(
                "GET /admin HTTP/1.1"));
        assertFalse(PairingWindowPolicy.permittedRequest(
                "GET /v1/pair/capabilities HTTP/1.1\r\nAuthorization: foo"));
        assertFalse(PairingWindowPolicy.permittedRequest(null));
    }

    @Test public void serviceHasDedicatedDiscoveryNamespace() {
        assertEquals("_kellies-pair._tcp.", PairingWindowPolicy.SERVICE_TYPE);
        assertNotEquals(HaDiscovery.SERVICE_TYPE, PairingWindowPolicy.SERVICE_TYPE);
    }
}
