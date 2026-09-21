package com.devil1716.bluetoothmanet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * API-21-safe file reads with an explicit size cap so large picks cannot OOM the process.
 */
public final class MeshIo {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final String MAX_FILE_LABEL = "2 MB";
    private static final int COPY_BUFFER = 8192;

    private MeshIo() {}

    /**
     * Tightens the 2 MB protocol cap on phones whose heap cannot hold a
     * transfer without paging the process out. Streaming keeps the working
     * set small, but a very low maxMemory still needs a smaller file.
     */
    public static int effectiveMaxFileBytes() {
        long heapCap = Runtime.getRuntime().maxMemory() / 8L;
        if (heapCap < 64L * 1024L) heapCap = 64L * 1024L;
        return (int) Math.min(MAX_FILE_BYTES, heapCap);
    }

    public static int chunkCount(int sizeBytes, int chunkSize) {
        if (sizeBytes <= 0 || chunkSize <= 0) return 0;
        return (sizeBytes + chunkSize - 1) / chunkSize;
    }

    public static byte[] readBounded(File file, int maxBytes) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("missing");
        }
        long length = file.length();
        if (length > maxBytes) {
            throw new FileTooLargeException(length, maxBytes);
        }
        try (FileInputStream input = new FileInputStream(file)) {
            return readBounded(input, maxBytes);
        }
    }

    public static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        if (input == null) {
            throw new IOException("missing");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[COPY_BUFFER];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new FileTooLargeException(total, maxBytes);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static int copyBounded(InputStream input, File dest, int maxBytes) throws IOException {
        if (input == null) throw new IOException("missing");
        if (dest == null) throw new IOException("missing dest");
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent.getAbsolutePath());
        }
        int total = 0;
        try (OutputStream output = new FileOutputStream(dest)) {
            byte[] buffer = new byte[COPY_BUFFER];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    dest.delete();
                    throw new FileTooLargeException(total, maxBytes);
                }
                output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            dest.delete();
            throw error;
        }
        if (total == 0) {
            dest.delete();
            throw new IOException("empty");
        }
        return total;
    }

    public static void copyFile(File source, File dest) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(dest)) {
            byte[] buffer = new byte[COPY_BUFFER];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    public static boolean moveOrCopy(File source, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent.getAbsolutePath());
        }
        if (source.renameTo(dest)) return true;
        copyFile(source, dest);
        return source.delete();
    }

    public static int readChunk(RandomAccessFile raf, byte[] scratch, int index, int chunkSize, int totalSize)
            throws IOException {
        int start = index * chunkSize;
        if (start < 0 || start >= totalSize) return 0;
        int length = Math.min(chunkSize, totalSize - start);
        raf.seek(start);
        raf.readFully(scratch, 0, length);
        return length;
    }

    public static boolean exceedsLimit(long sizeBytes, int maxBytes) {
        return sizeBytes > maxBytes;
    }

    public static final class FileTooLargeException extends IOException {
        public final long actualBytes;
        public final int maxBytes;

        public FileTooLargeException(long actualBytes, int maxBytes) {
            super("too_large");
            this.actualBytes = actualBytes;
            this.maxBytes = maxBytes;
        }
    }
}
