package com.devil1716.bluetoothmanet;

import com.devil1716.bluetoothmanet.crypto.MeshIntegrity;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Rebuilds a mesh file on disk instead of holding every chunk in RAM.
 * Early chunks that arrive before the signed header are kept in a tiny
 * in-memory window, then flushed once the size is known.
 */
public final class FileReassembly implements Closeable {
    static final int MAX_EARLY_CHUNKS = 16;

    private final File scratch;
    private final Map<Integer, byte[]> early = new HashMap<>();
    private final BitSet received = new BitSet();
    private RandomAccessFile raf;
    private int total;
    private int size;
    private int receivedCount;
    private boolean metaOk;

    public FileReassembly(File scratch) {
        this.scratch = scratch;
    }

    public synchronized boolean setMeta(int total, int size) throws IOException {
        if (metaOk) return this.total == total && this.size == size;
        if (total <= 0 || size <= 0 || size > MeshIo.effectiveMaxFileBytes()) return false;
        int expected = MeshIo.chunkCount(size, FilePacket.CHUNK_SIZE);
        if (total != expected) return false;
        this.total = total;
        this.size = size;
        this.metaOk = true;
        if (raf == null) {
            File parent = scratch.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent.getAbsolutePath());
            }
            raf = new RandomAccessFile(scratch, "rw");
            raf.setLength(size);
        }
        for (Map.Entry<Integer, byte[]> entry : early.entrySet()) {
            putChunk(entry.getKey(), entry.getValue());
        }
        early.clear();
        return true;
    }

    public synchronized boolean putChunk(int index, byte[] data) throws IOException {
        if (data == null || data.length == 0 || index < 0) return false;
        if (data.length > FilePacket.CHUNK_SIZE) return false;
        if (!metaOk) {
            if (early.size() >= MAX_EARLY_CHUNKS && !early.containsKey(index)) return false;
            early.put(index, data);
            return true;
        }
        if (index >= total) return false;
        int expected = expectedChunkLength(index);
        if (data.length != expected) return false;
        if (received.get(index)) return true;
        raf.seek((long) index * FilePacket.CHUNK_SIZE);
        raf.write(data);
        received.set(index);
        receivedCount++;
        return true;
    }

    public synchronized int receivedCount() {
        return receivedCount;
    }

    public synchronized int total() {
        return total;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean metaOk() {
        return metaOk;
    }

    public synchronized boolean isComplete() {
        return metaOk && total > 0 && receivedCount == total;
    }

    public synchronized boolean hasChunk(int index) {
        return received.get(index);
    }

    public synchronized String missingIndexes(int maxListed) {
        if (!metaOk) return "";
        StringBuilder missing = new StringBuilder();
        int listed = 0;
        for (int i = 0; i < total; i++) {
            if (received.get(i)) continue;
            if (missing.length() > 0) missing.append(',');
            missing.append(i);
            listed++;
            if (listed >= maxListed) break;
        }
        return missing.toString();
    }

    public synchronized boolean matchesSha256(String expectedHex) throws IOException {
        if (!isComplete() || expectedHex == null || expectedHex.isEmpty()) return false;
        raf.getFD().sync();
        closeQuietly();
        return MeshIntegrity.sha256Hex(scratch).equalsIgnoreCase(expectedHex);
    }

    public synchronized File finish() throws IOException {
        if (!isComplete()) throw new IOException("incomplete");
        closeQuietly();
        return scratch;
    }

    public synchronized void discard() {
        closeQuietly();
        if (scratch.exists()) scratch.delete();
        early.clear();
        received.clear();
        receivedCount = 0;
        metaOk = false;
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    private int expectedChunkLength(int index) {
        if (index < total - 1) return FilePacket.CHUNK_SIZE;
        int rem = size - index * FilePacket.CHUNK_SIZE;
        return Math.max(0, rem);
    }

    private void closeQuietly() {
        if (raf == null) return;
        try {
            raf.close();
        } catch (IOException ignored) {
        }
        raf = null;
    }
}
