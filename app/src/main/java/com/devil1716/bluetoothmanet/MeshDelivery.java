package com.devil1716.bluetoothmanet;

/**
 * Delivery and forwarding rules for the live Java mesh path
 * ({@link BluetoothMeshManager}), independent of Android APIs so they can be unit tested.
 */
public final class MeshDelivery {
    private MeshDelivery() {}

    /**
     * Split-horizon: when relaying a packet that arrived from {@code incomingPeer},
     * do not send it back out that same link.
     * Locally generated ACKs must use the reverse path, so they must not exclude
     * the incoming peer — that may be the only usable return connection.
     */
    public static String exceptAddress(boolean locallyGeneratedAck, String incomingPeer) {
        if (locallyGeneratedAck) return null;
        return incomingPeer;
    }

    public static MessageStatus statusAfterQueueAttempt(boolean hasPeers, boolean wroteToPeer) {
        if (wroteToPeer) return MessageStatus.SENT;
        return MessageStatus.QUEUED;
    }

    public static boolean shouldMarkSeenBeforeAuth() {
        return false;
    }

    public static boolean shouldDropPendingAfterForward() {
        return false;
    }

    public static boolean shouldOverwritePinnedKey(String existingHex, String advertisedHex) {
        if (existingHex == null || existingHex.isEmpty()) return true;
        if (advertisedHex == null || advertisedHex.isEmpty()) return false;
        return existingHex.equalsIgnoreCase(advertisedHex);
    }

    /**
     * If the original ACK was lost, the sender retries the same message ID.
     * The destination must not insert a duplicate chat row, but it should send
     * another ACK so the sender can move Queued/Sent → Delivered.
     */
    public static boolean shouldResendAckOnDuplicate(boolean isChatMessage, boolean destinedForMe) {
        return isChatMessage && destinedForMe;
    }

    /** Bounded exponential backoff for pending retries. Jitter is applied by the caller. */
    public static long retryDelayMs(int attempt) {
        int capped = Math.min(Math.max(attempt, 0), 7);
        long delay = 8_000L * (1L << capped);
        long max = 15L * 60L * 1000L;
        return Math.min(delay, max);
    }
}
