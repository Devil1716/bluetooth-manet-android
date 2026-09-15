package com.devil1716.bluetoothmanet.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPresenceTest {
    private val ttl = 30_000L

    private fun presence() = MeshPresence(ttlMs = ttl)

    @Test
    fun advertisingPeerIsVisibleBeforeAnyLinkExists() {
        val presence = presence()
        assertTrue(presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -55, 1_000L))

        val roster = presence.snapshot(1_000L)
        assertEquals(1, roster.size)
        assertEquals("K7M2", roster[0].nodeId)
        assertEquals(-55, roster[0].rssi)
        assertFalse("A scanned peer is not linked yet", roster[0].linked)
    }

    @Test
    fun peerKeepsItsSlotWhenTheLinkDropsButItIsStillAdvertising() {
        val presence = presence()
        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -55, 1_000L)
        presence.onLinked("AA:BB:CC:DD:EE:01", "K7M2", 1_100L)
        assertTrue(presence.snapshot(1_100L).single().linked)

        assertTrue(presence.onUnlinked("AA:BB:CC:DD:EE:01", 1_200L))
        val roster = presence.snapshot(1_200L)
        assertEquals(1, roster.size)
        assertFalse(roster.single().linked)
    }

    @Test
    fun peerThatWalksAwayExpires() {
        val presence = presence()
        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -55, 1_000L)

        assertEquals(1, presence.snapshot(1_000L + ttl).size)
        assertTrue(presence.snapshot(1_001L + ttl).isEmpty())
        assertTrue(presence.prune(1_001L + ttl))
        assertTrue(presence.snapshot(1_001L + ttl).isEmpty())
    }

    @Test
    fun linkedPeerSurvivesAnAdvertisingGap() {
        val presence = presence()
        presence.onLinked("AA:BB:CC:DD:EE:01", "K7M2", 1_000L)

        // A connected phone stops needing adverts to prove it is there.
        val wayLater = 1_000L + ttl * 5
        assertFalse(presence.prune(wayLater))
        assertEquals(1, presence.snapshot(wayLater).size)
    }

    @Test
    fun verifiedNodeIdOutranksWhateverTheAdvertisementClaims() {
        val presence = presence()
        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "WRONG", -55, 1_000L)
        assertTrue(presence.onVerifiedNodeId("AA:BB:CC:DD:EE:01", "K7M2", 1_100L))

        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "WRONG", -50, 1_200L)
        val peer = presence.snapshot(1_200L).single()
        assertEquals("K7M2", peer.nodeId)
        assertTrue(peer.verified)
    }

    @Test
    fun rosterIsOrderedByLinkThenSignalStrength() {
        val presence = presence()
        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "FAR", -90, 1_000L)
        presence.onAdvertisement("AA:BB:CC:DD:EE:02", "NEAR", -40, 1_000L)
        presence.onAdvertisement("AA:BB:CC:DD:EE:03", "LINKED", -95, 1_000L)
        presence.onLinked("AA:BB:CC:DD:EE:03", "LINKED", 1_000L)

        assertEquals(
            listOf("LINKED", "NEAR", "FAR"),
            presence.snapshot(1_000L).map { it.nodeId }
        )
    }

    @Test
    fun addressesAreCaseInsensitiveSoOnePhoneTakesOneSlot() {
        val presence = presence()
        presence.onAdvertisement("aa:bb:cc:dd:ee:01", "K7M2", -55, 1_000L)
        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -55, 1_100L)

        assertEquals(1, presence.snapshot(1_100L).size)
    }

    @Test
    fun onlyPeersStalledPastTheGraceWindowAreOfferedForDialling() {
        val presence = presence()
        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -55, 1_000L)

        assertTrue(presence.stalledPeers(6_000L, minimumAgeMs = 15_000L).isEmpty())
        assertEquals(
            listOf("AA:BB:CC:DD:EE:01"),
            presence.stalledPeers(16_000L, minimumAgeMs = 15_000L).map { it.address }
        )
    }

    @Test
    fun linkedPeersAreNeverOfferedForDialling() {
        val presence = presence()
        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -55, 1_000L)
        presence.onLinked("AA:BB:CC:DD:EE:01", "K7M2", 1_100L)

        assertTrue(presence.stalledPeers(30_000L, minimumAgeMs = 15_000L).isEmpty())
    }

    @Test
    fun repeatedAdvertisementsDoNotChurnTheRoster() {
        val presence = presence()
        presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -55, 1_000L)

        assertFalse(
            "Identical re-sightings must not force a UI rebuild",
            presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -55, 1_100L)
        )
        assertTrue(
            "A real signal swing is worth republishing",
            presence.onAdvertisement("AA:BB:CC:DD:EE:01", "K7M2", -80, 1_200L)
        )
    }

    @Test
    fun nodeIdsAreNormalizedFromNoisyAdvertisementBytes() {
        assertEquals("K7M2", MeshPresence.normalizeNodeId("k7m2"))
        assertEquals("K7M2", MeshPresence.normalizeNodeId("  K7M2\u0000 "))
        assertEquals("", MeshPresence.normalizeNodeId(null))
        assertEquals("", MeshPresence.normalizeNodeId("   "))
    }

    @Test
    fun labelPrefersNodeIdOverRadioAddress() {
        assertEquals("K7M2 (AA:BB)", MeshPeer("AA:BB", nodeId = "K7M2").label())
        assertEquals("BLE peer (AA:BB)", MeshPeer("AA:BB").label())
    }

    @Test
    fun exactlyOneSideOfAKnownPairDialsTheOther() {
        assertTrue(shouldInitiateLink("K7M2", "B4Q1"))
        assertFalse(shouldInitiateLink("B4Q1", "K7M2"))
    }

    @Test
    fun unknownPeerIdStillGetsDialled() {
        // Waiting on an ID we may never decode would leave the peer invisible,
        // and a duplicate link is harmless because packet IDs de-duplicate.
        assertTrue(shouldInitiateLink("B4Q1", null))
        assertTrue(shouldInitiateLink("B4Q1", ""))
    }

    @Test
    fun tieBreakIsCaseInsensitive() {
        assertTrue(shouldInitiateLink("k7m2", "B4Q1"))
        assertFalse(shouldInitiateLink("b4q1", "K7M2"))
    }
}
