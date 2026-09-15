package com.devil1716.bluetoothmanet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * API-21-safe file reads with an explicit size cap so large picks cannot OOM the process.
 */
public final class MeshIo {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    private MeshIo() {}

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
        byte[] buffer = new byte[8192];
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
