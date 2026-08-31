package com.devil1716.bluetoothmanet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FilePacketTest {
    @Test
    public void wireRoundTripIncludesNewlineOnBytes() {
        FilePacket packet = new FilePacket("id", "A", "B", 7, "notes.txt", 0, 1, "Zg==");
        FilePacket parsed = FilePacket.fromWire(packet.toWire());
        assertEquals("notes.txt", parsed.fileName);
        assertEquals(0, parsed.index);
        assertTrue(new String(packet.toBytes(), java.nio.charset.StandardCharsets.UTF_8).endsWith("\n"));
    }

    @Test
    public void ttlDecrementKeepsChunkMetadata() {
        FilePacket forwarded = new FilePacket("id", "A", "ALL", 4, "a.bin", 2, 9, "data").decrementedTtl();
        assertEquals(3, forwarded.ttl);
        assertEquals(2, forwarded.index);
        assertEquals(9, forwarded.total);
        assertTrue(forwarded.isFor("B"));
    }
}
