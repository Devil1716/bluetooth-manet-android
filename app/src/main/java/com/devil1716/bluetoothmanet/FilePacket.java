package com.devil1716.bluetoothmanet;

import java.nio.charset.StandardCharsets;

public class FilePacket {
    public enum Kind { CHUNK, META, REQ }

    public static final int CHUNK_SIZE = 600;
    public final Kind kind;
    public final String id, source, destination, fileName, data, digest, signature, requestIndexes;
    public final int ttl, index, total, size;

    public FilePacket(String id, String source, String destination, int ttl, String fileName, int index, int total, String data) {
        this(Kind.CHUNK, id, source, destination, ttl, fileName, index, total, 0, "", data, "");
    }

    public FilePacket(Kind kind, String id, String source, String destination, int ttl, String fileName,
                      int index, int total, int size, String digest, String data, String signature) {
        this(kind, id, source, destination, ttl, fileName, index, total, size, digest, data, signature, "");
    }

    public FilePacket(Kind kind, String id, String source, String destination, int ttl, String fileName,
                      int index, int total, int size, String digest, String data, String signature, String requestIndexes) {
        this.kind = kind;
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.ttl = ttl;
        this.fileName = fileName;
        this.index = index;
        this.total = total;
        this.size = size;
        this.digest = digest == null ? "" : digest;
        this.data = data == null ? "" : data;
        this.signature = signature == null ? "" : signature;
        this.requestIndexes = requestIndexes == null ? "" : requestIndexes;
    }

    public static FilePacket meta(String id, String source, String destination, int ttl, String fileName,
                                  int total, int size, String fileSha, String signature) {
        return new FilePacket(Kind.META, id, source, destination, ttl, fileName, -1, total, size, fileSha, "", signature);
    }

    public static FilePacket request(String id, String source, String destination, int ttl, String indexes) {
        return new FilePacket(Kind.REQ, id, source, destination, ttl, "", -1, 0, 0, "", "", "", indexes);
    }

    public static FilePacket fromWire(String wire) {
        if (wire.startsWith("FILEMETA|")) {
            String[] p = wire.split("\\|", 10);
            if (p.length != 10) throw new IllegalArgumentException("Invalid FILEMETA packet");
            return new FilePacket(Kind.META, p[1], p[2], p[3], Integer.parseInt(p[4]), p[5],
                    -1, Integer.parseInt(p[6]), Integer.parseInt(p[7]), p[8], "", p[9]);
        }
        if (wire.startsWith("FILEREQ|")) {
            String[] p = wire.split("\\|", 6);
            if (p.length != 6) throw new IllegalArgumentException("Invalid FILEREQ packet");
            return FilePacket.request(p[1], p[2], p[3], Integer.parseInt(p[4]), p[5]);
        }
        String[] p = wire.split("\\|", 11);
        if (p.length == 9 && "FILE".equals(p[0])) {
            return new FilePacket(p[1], p[2], p[3], Integer.parseInt(p[4]), p[5],
                    Integer.parseInt(p[6]), Integer.parseInt(p[7]), p[8]);
        }
        if (p.length != 11 || !"FILE".equals(p[0])) throw new IllegalArgumentException("Invalid FILE packet");
        return new FilePacket(Kind.CHUNK, p[1], p[2], p[3], Integer.parseInt(p[4]), p[5],
                Integer.parseInt(p[6]), Integer.parseInt(p[7]), 0, p[8], p[9], p[10]);
    }

    public String toWire() {
        switch (kind) {
            case META:
                return String.format("FILEMETA|%s|%s|%s|%d|%s|%d|%d|%s|%s",
                        id, source, destination, ttl, fileName, total, size, digest, signature);
            case REQ:
                return String.format("FILEREQ|%s|%s|%s|%d|%s", id, source, destination, ttl, requestIndexes);
            default:
                if (digest.isEmpty() && signature.isEmpty()) {
                    return String.format("FILE|%s|%s|%s|%d|%s|%d|%d|%s",
                            id, source, destination, ttl, fileName, index, total, data);
                }
                return String.format("FILE|%s|%s|%s|%d|%s|%d|%d|%s|%s|%s",
                        id, source, destination, ttl, fileName, index, total, digest, data, signature);
        }
    }

    public byte[] toBytes() { return (toWire() + "\n").getBytes(StandardCharsets.UTF_8); }

    public FilePacket decrementedTtl() {
        return new FilePacket(kind, id, source, destination, ttl - 1, fileName, index, total, size, digest, data, signature, requestIndexes);
    }

    public boolean isFor(String nodeId) {
        return destination.equalsIgnoreCase(nodeId)
                || "ALL".equalsIgnoreCase(destination)
                || "*".equals(destination);
    }
}
