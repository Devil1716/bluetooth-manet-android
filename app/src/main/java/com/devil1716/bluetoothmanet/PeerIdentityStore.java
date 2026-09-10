package com.devil1716.bluetoothmanet;

import com.devil1716.bluetoothmanet.crypto.PacketSigner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pins a node ID to the first verified public key seen for it.
 * A later HELLO advertising a different key is rejected rather than silently
 * replacing the mapping (identity substitution).
 */
public class PeerIdentityStore {
    public enum PutResult { ACCEPTED, UNCHANGED, REJECTED_MISMATCH }

    private final File file;
    private final ConcurrentHashMap<String, String> keys = new ConcurrentHashMap<>();

    public PeerIdentityStore(File file) {
        this.file = file;
        load();
    }

    public String get(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) return null;
        return keys.get(nodeId.trim().toUpperCase(Locale.US));
    }

    public PutResult putVerified(String nodeId, String publicKeyHex) {
        if (nodeId == null || publicKeyHex == null) return PutResult.REJECTED_MISMATCH;
        String id = nodeId.trim().toUpperCase(Locale.US);
        String key = publicKeyHex.trim();
        if (id.isEmpty() || key.isEmpty()) return PutResult.REJECTED_MISMATCH;
        String existing = keys.get(id);
        if (existing == null) {
            keys.put(id, key);
            persist();
            return PutResult.ACCEPTED;
        }
        if (existing.equalsIgnoreCase(key)) return PutResult.UNCHANGED;
        return PutResult.REJECTED_MISMATCH;
    }

    public String fingerprint(String nodeId) {
        String key = get(nodeId);
        if (key == null) return "";
        return fingerprintHex(key);
    }

    public static String fingerprintHex(String publicKeyHex) {
        try {
            byte[] raw = PacketSigner.fromHex(publicKeyHex.trim());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            String hex = PacketSigner.toHex(digest).substring(0, 16);
            return hex.substring(0, 8) + " " + hex.substring(8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public Map<String, String> snapshot() {
        return new ConcurrentHashMap<>(keys);
    }

    private void load() {
        if (file == null || !file.isFile()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int space = trimmed.indexOf(' ');
                if (space <= 0) continue;
                keys.put(trimmed.substring(0, space).toUpperCase(Locale.US), trimmed.substring(space + 1).trim());
            }
        } catch (IOException ignored) {
        }
    }

    private void persist() {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write("# nodeId publicKeyHex — do not edit while MESH is running\n");
            for (Map.Entry<String, String> entry : keys.entrySet()) {
                writer.write(entry.getKey());
                writer.write(' ');
                writer.write(entry.getValue());
                writer.write('\n');
            }
        } catch (IOException ignored) {
        }
    }
}
