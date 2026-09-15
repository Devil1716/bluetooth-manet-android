package com.devil1716.bluetoothmanet;

import java.util.List;

/** A non-RFCOMM transport the mesh manager can hand packets to. */
public interface MeshLinkBridge {
    int send(byte[] bytes, String exceptPeerId);
    boolean hasPeers();
    List<String> peerLabels();

    /**
     * Tells the transport which mesh node sits behind a peer address, once a
     * signed handshake has proved it. The transport only knows the Bluetooth
     * address until this point, so without it nearby peers are labelled by
     * radio address instead of by node ID.
     */
    default void noteVerifiedNodeId(String peerId, String peerNodeId) { }
}
