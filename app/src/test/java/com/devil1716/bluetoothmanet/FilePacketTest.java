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

    @Test
    public void signedChunkRoundTrip() {
        FilePacket packet = new FilePacket(FilePacket.Kind.CHUNK, "id", "A", "B", 7, "notes.txt",
                0, 1, 4, "abc123", "Zg==", "sig");
        FilePacket parsed = FilePacket.fromWire(packet.toWire());
        assertEquals("abc123", parsed.digest);
        assertEquals("sig", parsed.signature);
        assertEquals(FilePacket.Kind.CHUNK, parsed.kind);
    }

    @Test
    public void metaRoundTrip() {
        FilePacket meta = FilePacket.meta("id", "A", "B", 7, "a.bin", 3, 1200, "ffff", "sig");
        FilePacket parsed = FilePacket.fromWire(meta.toWire());
        assertEquals(FilePacket.Kind.META, parsed.kind);
        assertEquals(1200, parsed.size);
        assertEquals("ffff", parsed.digest);
    }
}
