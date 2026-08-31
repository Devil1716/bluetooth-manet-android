package com.devil1716.bluetoothmanet;

import java.util.List;

public interface MeshLinkBridge {
    int send(byte[] bytes, String exceptPeerId);
    boolean hasPeers();
    List<String> peerLabels();
}
