package com.devil1716.bluetoothmanet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ManetMessageTest {
    @Test
    public void parsesWireFormat() {
        ManetMessage message = ManetMessage.fromWire("123|A|C|3|Hello world");

        assertEquals("123", message.getId());
        assertEquals("A", message.getSource());
        assertEquals("C", message.getDestination());
        assertEquals(3, message.getTtl());
        assertEquals("Hello world", message.getData());
    }

    @Test
    public void decrementedTtlPreservesPayload() {
        ManetMessage message = new ManetMessage("123", "A", "C", 3, "Hello");
        ManetMessage forwarded = message.decrementedTtl();

        assertEquals("123", forwarded.getId());
        assertEquals("A", forwarded.getSource());
        assertEquals("C", forwarded.getDestination());
        assertEquals(2, forwarded.getTtl());
        assertEquals("Hello", forwarded.getData());
    }
}
