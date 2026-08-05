package com.devil1716.bluetoothmanet;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class ManetMessage {
    public static final int DEFAULT_TTL = 3;
    public enum Type { MSG, ACK, HELLO }

    private final Type type;
    private final String id;
    private final String source;
    private final String destination;
    private final int ttl;
    private final String data;

    public ManetMessage(String id, String source, String destination, int ttl, String data) {
        this(Type.MSG, id, source, destination, ttl, data);
    }

    public ManetMessage(Type type, String id, String source, String destination, int ttl, String data) {
        this.type = type;
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.ttl = ttl;
        this.data = data;
    }

    public static ManetMessage outbound(String source, String destination, String data, int ttl) {
        return new ManetMessage(UUID.randomUUID().toString(), source, destination, ttl, data);
    }

    public static ManetMessage fromWire(String payload) {
        String[] parts = payload.split("\\|", 6);
        if (parts.length == 5) {
            return new ManetMessage(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]), parts[4]);
        }
        if (parts.length != 6) throw new IllegalArgumentException("Expected TYPE|ID|SRC|DEST|TTL|DATA");
        return new ManetMessage(Type.valueOf(parts[0].toUpperCase(Locale.US)), parts[1], parts[2], parts[3],
                Integer.parseInt(parts[4]), parts[5]);
    }

    public String toWire() {
        return String.format(Locale.US, "%s|%s|%s|%s|%d|%s", type.name(), id, source, destination, ttl, data);
    }

    public byte[] toBytes() {
        return (toWire() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public ManetMessage decrementedTtl() {
        return new ManetMessage(type, id, source, destination, ttl - 1, data);
    }

    public static ManetMessage ack(String source, String destination, String originalId) {
        return new ManetMessage(Type.ACK, UUID.randomUUID().toString(), source, destination,
                DEFAULT_TTL, originalId);
    }

    public static ManetMessage hello(String source) {
        return new ManetMessage(Type.HELLO, UUID.randomUUID().toString(), source, "*", 1, source);
    }

    public Type getType() { return type; }

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public int getTtl() {
        return ttl;
    }

    public String getData() {
        return data;
    }
}
