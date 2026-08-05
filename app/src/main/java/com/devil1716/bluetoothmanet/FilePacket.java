package com.devil1716.bluetoothmanet;

import java.nio.charset.StandardCharsets;

public class FilePacket {
    public static final int CHUNK_SIZE = 800;
    public final String id, source, destination, fileName, data;
    public final int ttl, index, total;
    public FilePacket(String id, String source, String destination, int ttl, String fileName, int index, int total, String data) {
        this.id=id; this.source=source; this.destination=destination; this.ttl=ttl; this.fileName=fileName;
        this.index=index; this.total=total; this.data=data;
    }
    public static FilePacket fromWire(String wire) {
        String[] p = wire.split("\\|", 9);
        if (p.length != 9 || !"FILE".equals(p[0])) throw new IllegalArgumentException("Invalid FILE packet");
        return new FilePacket(p[1],p[2],p[3],Integer.parseInt(p[4]),p[5],Integer.parseInt(p[6]),Integer.parseInt(p[7]),p[8]);
    }
    public String toWire() { return String.format("FILE|%s|%s|%s|%d|%s|%d|%d|%s",id,source,destination,ttl,fileName,index,total,data); }
    public byte[] toBytes() { return (toWire()+"\n").getBytes(StandardCharsets.UTF_8); }
}
