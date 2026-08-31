package com.devil1716.bluetoothmanet;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class MeshFileStore {
    private MeshFileStore() { }

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
