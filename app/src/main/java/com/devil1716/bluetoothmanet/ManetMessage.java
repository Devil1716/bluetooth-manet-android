package com.devil1716.bluetoothmanet;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class ManetMessage {
    public static final int DEFAULT_TTL = 3;

    private final String id;
    private final String source;
    private final String destination;
    private final int ttl;
    private final String data;

    public ManetMessage(String id, String source, String destination, int ttl, String data) {
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
        String[] parts = payload.split("\\|", 5);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Expected format ID|SRC|DEST|TTL|DATA");
        }
        return new ManetMessage(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]), parts[4]);
    }

    public String toWire() {
        return String.format(Locale.US, "%s|%s|%s|%d|%s", id, source, destination, ttl, data);
    }

    public byte[] toBytes() {
        return (toWire() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public ManetMessage decrementedTtl() {
        return new ManetMessage(id, source, destination, ttl - 1, data);
    }

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
