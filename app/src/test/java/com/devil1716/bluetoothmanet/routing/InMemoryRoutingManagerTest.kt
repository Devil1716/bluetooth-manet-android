package com.devil1716.bluetoothmanet.routing

import com.devil1716.bluetoothmanet.mesh.domain.MeshPacket
import com.devil1716.bluetoothmanet.mesh.domain.Neighbor
import com.devil1716.bluetoothmanet.mesh.domain.PacketType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryRoutingManagerTest {
    @Test fun `forwards through the best direct neighbor`() = runTest {
        var nextHop: String? = null
        val manager = InMemoryRoutingManager(PacketForwarder { hop, _ -> nextHop = hop; Result.success(Unit) })
        manager.updateNeighbor(Neighbor("david", "David", -45, 90, 20, 1, System.currentTimeMillis(), true))

        val result = manager.forward(MeshPacket(sourceId = "alice", destinationId = "david", type = PacketType.MESSAGE, encryptedPayload = byteArrayOf(1)))

        assertTrue(result.isSuccess)
        assertEquals("david", nextHop)
    }
}
