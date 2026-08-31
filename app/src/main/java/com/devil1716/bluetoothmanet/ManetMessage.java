package com.devil1716.bluetoothmanet;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class ManetMessage {
    public static final int DEFAULT_TTL = 7;
    public enum Type { MSG, ACK, HELLO }

    private final Type type;
    private final String id;
    private final String source;
    private final String destination;
    private final int ttl;
    private final String data;
    private final String signature;

    public ManetMessage(String id, String source, String destination, int ttl, String data) {
        this(Type.MSG, id, source, destination, ttl, data, null);
    }

    public ManetMessage(Type type, String id, String source, String destination, int ttl, String data) {
        this(type, id, source, destination, ttl, data, null);
    }

    public ManetMessage(Type type, String id, String source, String destination, int ttl, String data, String signature) {
        this.type = type;
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.ttl = ttl;
        this.data = data;
        this.signature = signature;
    }

    public static ManetMessage outbound(String source, String destination, String data, int ttl) {
        return new ManetMessage(Type.MSG, UUID.randomUUID().toString(), source, destination, ttl, data, null);
    }

    public static ManetMessage fromWire(String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length == 5) {
            return new ManetMessage(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]), parts[4]);
        }
        if (parts.length == 6) {
            return new ManetMessage(Type.valueOf(parts[0].toUpperCase(Locale.US)), parts[1], parts[2], parts[3],
                    Integer.parseInt(parts[4]), parts[5], null);
        }
        if (parts.length < 7) throw new IllegalArgumentException("Expected TYPE|ID|SRC|DEST|TTL|DATA[|SIG]");
        StringBuilder data = new StringBuilder(parts[5]);
        for (int i = 6; i < parts.length - 1; i++) {
            data.append('|').append(parts[i]);
        }
        return new ManetMessage(Type.valueOf(parts[0].toUpperCase(Locale.US)), parts[1], parts[2], parts[3],
                Integer.parseInt(parts[4]), data.toString(), parts[parts.length - 1]);
    }

    public String toWire() {
        String body = String.format(Locale.US, "%s|%s|%s|%s|%d|%s", type.name(), id, source, destination, ttl, data);
        return signature == null || signature.isEmpty() ? body : body + "|" + signature;
    }

    public byte[] toBytes() {
        return (toWire() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public ManetMessage decrementedTtl() {
        return new ManetMessage(type, id, source, destination, ttl - 1, data, signature);
    }

    public ManetMessage withSignature(String signature) {
        return new ManetMessage(type, id, source, destination, ttl, data, signature);
    }

    public String canonical() {
        return com.devil1716.bluetoothmanet.crypto.MeshIntegrity.messageCanon(type.name(), id, source, destination, data);
    }

    public static ManetMessage ack(String source, String destination, String originalId) {
        return new ManetMessage(Type.ACK, UUID.randomUUID().toString(), source, destination,
                DEFAULT_TTL, originalId, null);
    }

    public static ManetMessage hello(String source) {
        return new ManetMessage(Type.HELLO, UUID.randomUUID().toString(), source, "*", DEFAULT_TTL, source, null);
    }

    public static ManetMessage hello(String source, String publicKeyHex, String signature) {
        return new ManetMessage(Type.HELLO, UUID.randomUUID().toString(), source, "*", DEFAULT_TTL,
                source + "::" + publicKeyHex, signature);
    }

    public Type getType() { return type; }
    public String getId() { return id; }
    public String getSource() { return source; }
    public String getDestination() { return destination; }
    public int getTtl() { return ttl; }
    public String getData() { return data; }
    public String getSignature() { return signature; }
    public boolean hasSignature() { return signature != null && !signature.isEmpty(); }
}
