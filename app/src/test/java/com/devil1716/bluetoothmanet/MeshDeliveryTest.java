package com.devil1716.bluetoothmanet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MeshDeliveryTest {
    @Test
    public void locallyGeneratedAckDoesNotExcludeIncomingPeer() {
        assertNull(MeshDelivery.exceptAddress(true, "AA:BB:CC:DD:EE:FF"));
    }

    @Test
    public void relayedPacketsExcludeIncomingPeer() {
        assertEquals("AA:BB:CC:DD:EE:FF", MeshDelivery.exceptAddress(false, "AA:BB:CC:DD:EE:FF"));
    }

    @Test
    public void offlineSendStaysQueuedNotFailed() {
        assertEquals(MessageStatus.QUEUED, MeshDelivery.statusAfterQueueAttempt(false, false));
        assertEquals(MessageStatus.QUEUED, MeshDelivery.statusAfterQueueAttempt(true, false));
        assertEquals(MessageStatus.SENT, MeshDelivery.statusAfterQueueAttempt(true, true));
    }

    @Test
    public void pendingSurvivesForwardUntilAck() {
        assertFalse(MeshDelivery.shouldDropPendingAfterForward());
    }

    @Test
    public void authHappensBeforeDedup() {
        assertFalse(MeshDelivery.shouldMarkSeenBeforeAuth());
    }

    @Test
    public void pinnedKeysAreNotSilentlyReplaced() {
        assertTrue(MeshDelivery.shouldOverwritePinnedKey(null, "abc"));
        assertTrue(MeshDelivery.shouldOverwritePinnedKey("abc", "ABC"));
        assertFalse(MeshDelivery.shouldOverwritePinnedKey("abc", "def"));
    }

    @Test
    public void lostAckTriggersResendWithoutDuplicateChatRow() {
        assertTrue(MeshDelivery.shouldResendAckOnDuplicate(true, true));
        assertFalse(MeshDelivery.shouldResendAckOnDuplicate(true, false));
        assertFalse(MeshDelivery.shouldResendAckOnDuplicate(false, true));
    }

    @Test
    public void pendingRetryBackoffIsBounded() {
        assertEquals(8_000L, MeshDelivery.retryDelayMs(0));
        assertEquals(16_000L, MeshDelivery.retryDelayMs(1));
        assertEquals(15L * 60L * 1000L, MeshDelivery.retryDelayMs(20));
    }
}
