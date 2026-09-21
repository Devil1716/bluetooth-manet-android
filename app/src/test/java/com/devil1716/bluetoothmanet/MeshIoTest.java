package com.devil1716.bluetoothmanet;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MeshIoTest {
    @Test
    public void readsSmallStream() throws Exception {
        byte[] expected = new byte[] {1, 2, 3, 4};
        byte[] actual = MeshIo.readBounded(new ByteArrayInputStream(expected), 16);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void rejectsOversizeStreamWithoutReadingUnbounded() throws Exception {
        byte[] tooBig = new byte[32];
        try {
            MeshIo.readBounded(new ByteArrayInputStream(tooBig), 16);
            fail("expected FileTooLargeException");
        } catch (MeshIo.FileTooLargeException error) {
            assertEquals(16, error.maxBytes);
            assertTrue(error.actualBytes > 16);
        }
    }

    @Test
    public void rejectsOversizeFileByLength() throws Exception {
        File file = Files.createTempFile("mesh-io", ".bin").toFile();
        Files.write(file.toPath(), new byte[64]);
        try {
            MeshIo.readBounded(file, 8);
            fail("expected FileTooLargeException");
        } catch (MeshIo.FileTooLargeException error) {
            assertEquals(64L, error.actualBytes);
        } finally {
            file.delete();
        }
    }

    @Test
    public void copiesStreamToFileWithCap() throws Exception {
        File dest = Files.createTempFile("mesh-copy", ".bin").toFile();
        try {
            int copied = MeshIo.copyBounded(new ByteArrayInputStream(new byte[] {9, 8, 7}), dest, 16);
            assertEquals(3, copied);
            assertArrayEquals(new byte[] {9, 8, 7}, Files.readAllBytes(dest.toPath()));
        } finally {
            dest.delete();
        }
    }

    @Test
    public void copyBoundedDeletesDestWhenOversize() throws Exception {
        File dest = Files.createTempFile("mesh-copy-big", ".bin").toFile();
        try {
            MeshIo.copyBounded(new ByteArrayInputStream(new byte[32]), dest, 8);
            fail("expected FileTooLargeException");
        } catch (MeshIo.FileTooLargeException error) {
            assertEquals(8, error.maxBytes);
            assertFalse(dest.exists());
        } finally {
            dest.delete();
        }
    }

    @Test
    public void sizeHelper() {
        assertTrue(MeshIo.exceedsLimit(MeshIo.MAX_FILE_BYTES + 1L, MeshIo.MAX_FILE_BYTES));
        assertFalse(MeshIo.exceedsLimit(MeshIo.MAX_FILE_BYTES, MeshIo.MAX_FILE_BYTES));
        assertTrue(MeshIo.effectiveMaxFileBytes() > 0);
        assertTrue(MeshIo.effectiveMaxFileBytes() <= MeshIo.MAX_FILE_BYTES);
        assertEquals(4, MeshIo.chunkCount(2000, 600));
        assertEquals(1, MeshIo.chunkCount(600, 600));
    }
}
