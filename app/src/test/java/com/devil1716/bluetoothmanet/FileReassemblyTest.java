package com.devil1716.bluetoothmanet;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileReassemblyTest {
    @Test
    public void writesOutOfOrderChunksToDiskAndVerifiesHash() throws Exception {
        byte[] payload = new byte[FilePacket.CHUNK_SIZE + 40];
        Arrays.fill(payload, 0, FilePacket.CHUNK_SIZE, (byte) 7);
        Arrays.fill(payload, FilePacket.CHUNK_SIZE, payload.length, (byte) 9);
        String sha = com.devil1716.bluetoothmanet.crypto.MeshIntegrity.sha256Hex(payload);

        File scratch = Files.createTempFile("mesh-rx", ".bin").toFile();
        try (FileReassembly reassembly = new FileReassembly(scratch)) {
            byte[] second = Arrays.copyOfRange(payload, FilePacket.CHUNK_SIZE, payload.length);
            assertTrue(reassembly.putChunk(1, second));
            assertTrue(reassembly.setMeta(2, payload.length));
            byte[] first = Arrays.copyOfRange(payload, 0, FilePacket.CHUNK_SIZE);
            assertTrue(reassembly.putChunk(0, first));
            assertTrue(reassembly.isComplete());
            assertTrue(reassembly.matchesSha256(sha));
            File finished = reassembly.finish();
            assertArrayEquals(payload, Files.readAllBytes(finished.toPath()));
        } finally {
            scratch.delete();
        }
    }

    @Test
    public void rejectsInvalidSizeAndCapsEarlyChunks() throws Exception {
        File scratch = Files.createTempFile("mesh-rx-bad", ".bin").toFile();
        try (FileReassembly reassembly = new FileReassembly(scratch)) {
            assertFalse(reassembly.setMeta(1, MeshIo.MAX_FILE_BYTES + 1));
            assertFalse(reassembly.setMeta(99, 100));
            byte[] chunk = new byte[FilePacket.CHUNK_SIZE];
            for (int i = 0; i < FileReassembly.MAX_EARLY_CHUNKS; i++) {
                assertTrue(reassembly.putChunk(i, chunk));
            }
            assertFalse(reassembly.putChunk(FileReassembly.MAX_EARLY_CHUNKS, chunk));
            assertEquals("", reassembly.missingIndexes(8));
        } finally {
            scratch.delete();
        }
    }

    @Test
    public void acceptsLegacySixHundredByteChunks() throws Exception {
        byte[] payload = new byte[2000];
        Arrays.fill(payload, (byte) 3);
        int stride = 600;
        int total = MeshIo.chunkCount(payload.length, stride);
        File scratch = Files.createTempFile("mesh-rx-legacy", ".bin").toFile();
        try (FileReassembly reassembly = new FileReassembly(scratch)) {
            assertEquals(600, FileReassembly.inferStride(payload.length, total));
            assertTrue(reassembly.setMeta(total, payload.length));
            for (int i = 0; i < total; i++) {
                int start = i * stride;
                int end = Math.min(payload.length, start + stride);
                assertTrue(reassembly.putChunk(i, Arrays.copyOfRange(payload, start, end)));
            }
            assertTrue(reassembly.isComplete());
            assertTrue(reassembly.matchesSha256(
                    com.devil1716.bluetoothmanet.crypto.MeshIntegrity.sha256Hex(payload)));
        } finally {
            scratch.delete();
        }
    }

    @Test
    public void missingIndexesListsGaps() throws Exception {
        File scratch = Files.createTempFile("mesh-rx-gap", ".bin").toFile();
        byte[] chunk = new byte[FilePacket.CHUNK_SIZE];
        try (FileReassembly reassembly = new FileReassembly(scratch)) {
            assertTrue(reassembly.setMeta(4, FilePacket.CHUNK_SIZE * 4));
            assertTrue(reassembly.putChunk(0, chunk));
            assertTrue(reassembly.putChunk(2, chunk));
            assertEquals("1,3", reassembly.missingIndexes(8));
            assertFalse(reassembly.isComplete());
        } finally {
            scratch.delete();
        }
    }
}
