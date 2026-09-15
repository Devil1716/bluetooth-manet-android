package com.devil1716.bluetoothmanet.bluetooth

import java.util.Locale

/**
 * One nearby phone running MESH, as seen by the local radio.
 *
 * A peer is tracked from the first advertisement, long before a GATT link
 * exists, so the Nearby list can show who is around instead of only who is
 * already connected.
 */
data class MeshPeer(
    val address: String,
    val nodeId: String = "",
    val rssi: Int = 0,
    val lastSeenAt: Long = 0L,
    val firstSeenAt: Long = 0L,
    val linked: Boolean = false,
    val verified: Boolean = false
) {
    val hasNodeId: Boolean get() = nodeId.isNotBlank()

    fun label(): String = if (hasNodeId) "$nodeId ($address)" else "BLE peer ($address)"
}

/**
 * The roster of mesh phones currently in radio range.
 *
 * Entries expire, so a peer that walks away stops being reported as online
 * instead of lingering forever. Every mutator returns true only when the
 * roster actually changed in a way worth republishing, which keeps the UI from
 * being rebuilt on every single scan callback.
 */
class MeshPresence(private val ttlMs: Long = DEFAULT_TTL_MS) {
    private val peers = LinkedHashMap<String, MeshPeer>()

    /** A peer was seen in a scan. Returns true when the roster changed. */
    @Synchronized
    fun onAdvertisement(address: String, nodeId: String?, rssi: Int, now: Long): Boolean {
        val key = normalizeAddress(address) ?: return false
        val id = normalizeNodeId(nodeId)
        val existing = peers[key]
        if (existing == null) {
            peers[key] = MeshPeer(key, id, rssi, lastSeenAt = now, firstSeenAt = now)
            return true
        }
        // A verified node ID from a HELLO outranks whatever the advertisement claims.
        val resolvedId = if (existing.verified) existing.nodeId else id.ifEmpty { existing.nodeId }
        val updated = existing.copy(nodeId = resolvedId, rssi = rssi, lastSeenAt = now)
        peers[key] = updated
        return resolvedId != existing.nodeId || existing.isStale(now, ttlMs) ||
            significantRssiChange(existing.rssi, rssi)
    }

    /** A GATT link to this peer came up. */
    @Synchronized
    fun onLinked(address: String, nodeId: String?, now: Long): Boolean {
        val key = normalizeAddress(address) ?: return false
        val id = normalizeNodeId(nodeId)
        val existing = peers[key]
        val resolvedId = when {
            existing == null -> id
            existing.verified -> existing.nodeId
            else -> id.ifEmpty { existing.nodeId }
        }
        val updated = (existing ?: MeshPeer(key, firstSeenAt = now)).copy(
            nodeId = resolvedId,
            lastSeenAt = now,
            linked = true
        )
        peers[key] = updated
        return existing == null || !existing.linked || existing.nodeId != resolvedId
    }

    /** The GATT link dropped, but the peer may still be advertising nearby. */
    @Synchronized
    fun onUnlinked(address: String, now: Long): Boolean {
        val key = normalizeAddress(address) ?: return false
        val existing = peers[key] ?: return false
        if (!existing.linked) return false
        peers[key] = existing.copy(linked = false, lastSeenAt = now)
        return true
    }

    /** A signed HELLO proved which node sits behind this address. */
    @Synchronized
    fun onVerifiedNodeId(address: String, nodeId: String?, now: Long): Boolean {
        val key = normalizeAddress(address) ?: return false
        val id = normalizeNodeId(nodeId)
        if (id.isEmpty()) return false
        val existing = peers[key]
        if (existing != null && existing.verified && existing.nodeId == id) {
            peers[key] = existing.copy(lastSeenAt = now)
            return false
        }
        peers[key] = (existing ?: MeshPeer(key, firstSeenAt = now)).copy(
            nodeId = id,
            verified = true,
            lastSeenAt = now
        )
        return true
    }

    @Synchronized
    fun forget(address: String): Boolean {
        val key = normalizeAddress(address) ?: return false
        return peers.remove(key) != null
    }

    /** Drops peers that have not been heard from within the TTL. */
    @Synchronized
    fun prune(now: Long): Boolean {
        val iterator = peers.entries.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val peer = iterator.next().value
            // A live GATT link is proof of presence even without fresh adverts.
            if (!peer.linked && peer.isStale(now, ttlMs)) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    /** Peers in range right now: linked first, then by signal strength. */
    @Synchronized
    fun snapshot(now: Long): List<MeshPeer> = peers.values
        .filter { it.linked || !it.isStale(now, ttlMs) }
        .sortedWith(
            compareByDescending<MeshPeer> { it.linked }
                .thenByDescending { it.rssi }
                .thenByDescending { it.lastSeenAt }
        )

    /**
     * Peers we have known about for a while that still have no GATT link.
     *
     * These are the ones the tie-break rule may have parked: if the peer with
     * the higher node ID never dials us, somebody has to break the stalemate.
     */
    @Synchronized
    fun stalledPeers(now: Long, minimumAgeMs: Long): List<MeshPeer> = peers.values
        .filter { !it.linked && !it.isStale(now, ttlMs) }
        .filter { it.firstSeenAt > 0L && now - it.firstSeenAt >= minimumAgeMs }
        .sortedByDescending { it.rssi }

    @Synchronized
    fun clear() = peers.clear()

    companion object {
        const val DEFAULT_TTL_MS = 45_000L

        /** RSSI jitters constantly; only redraw when the change is meaningful. */
        private const val RSSI_NOISE_FLOOR = 6

        fun normalizeNodeId(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return ""
            val cleaned = trimmed.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            return cleaned.uppercase(Locale.US)
        }

        fun normalizeAddress(raw: String?): String? =
            raw?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }

        private fun significantRssiChange(old: Int, new: Int): Boolean =
            kotlin.math.abs(old - new) >= RSSI_NOISE_FLOOR

        private fun MeshPeer.isStale(now: Long, ttlMs: Long): Boolean =
            lastSeenAt <= 0L || now - lastSeenAt > ttlMs
    }
}

/**
 * Picks which side of a pair opens the GATT connection so both phones do not
 * dial each other at once.
 *
 * The node with the higher ID initiates. When the peer's ID has not been
 * decoded yet there is nothing to compare, so we dial anyway rather than wait
 * forever — a duplicate link is harmless because packet IDs are de-duplicated,
 * but a missed link is invisible to the user.
 */
fun shouldInitiateLink(localNodeId: String, remoteNodeId: String?): Boolean {
    val local = MeshPresence.normalizeNodeId(localNodeId)
    val remote = MeshPresence.normalizeNodeId(remoteNodeId)
    if (local.isEmpty() || remote.isEmpty()) return true
    if (local == remote) return true
    return local > remote
}
