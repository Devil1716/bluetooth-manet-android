package com.devil1716.bluetoothmanet;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class MeshFileStore {
    private MeshFileStore() { }

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
