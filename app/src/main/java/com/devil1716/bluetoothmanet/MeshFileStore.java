package com.devil1716.bluetoothmanet;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class MeshFileStore {
    private MeshFileStore() { }

    public static final int MAX_SEND_BYTES = 2 * 1024 * 1024;
    private static final char PATH_SEPARATOR = '\u0000';

    public static String chatLabel(String fileName, String path) {
        return "📎 " + (fileName == null ? "file" : fileName) + PATH_SEPARATOR + path;
    }

    public static String displayName(String text) {
        if (text == null) return "";
        int separator = text.indexOf(PATH_SEPARATOR);
        return separator >= 0 ? text.substring(0, separator) : text;
    }

    public static String embeddedPath(String text) {
        if (text == null) return null;
        int separator = text.indexOf(PATH_SEPARATOR);
        return separator >= 0 ? text.substring(separator + 1) : null;
    }

    public static boolean isFileMessage(String text) {
        return text != null && text.startsWith("📎 ") && text.indexOf(PATH_SEPARATOR) > 0;
    }

    public static byte[] readLimited(java.io.InputStream input, int maxBytes) throws IOException {
        if (input == null) throw new IOException("Could not open file");
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("File is larger than 2 MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static byte[] readLimited(File file, int maxBytes) throws IOException {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            return readLimited(input, maxBytes);
        }
    }

    public static File save(Context context, String fileName, byte[] bytes) throws IOException {
        File directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "received");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory.getAbsolutePath());
        }
        String safeName = fileName == null || fileName.trim().isEmpty()
                ? "mesh-file.bin"
                : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        File file = new File(directory, safeName);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        return file;
    }
}
