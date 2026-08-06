package com.devil1716.bluetoothmanet.routing

import com.devil1716.bluetoothmanet.mesh.domain.MeshPacket
import com.devil1716.bluetoothmanet.mesh.domain.Neighbor
import com.devil1716.bluetoothmanet.mesh.domain.Route
import com.devil1716.bluetoothmanet.mesh.domain.RoutingManager
import java.util.concurrent.ConcurrentHashMap

fun interface PacketForwarder {
    suspend fun send(nextHopId: String, packet: MeshPacket): Result<Unit>
}

class InMemoryRoutingManager(
    private val forwarder: PacketForwarder,
    private val routeTimeoutMs: Long = 90_000L
) : RoutingManager {
    private val neighbors = ConcurrentHashMap<String, Neighbor>()
    private val routes = ConcurrentHashMap<String, Route>()

    override suspend fun updateNeighbor(neighbor: Neighbor) {
        neighbors[neighbor.deviceId] = neighbor
        if (!neighbor.connected) return
        val route = Route(
            destinationId = neighbor.deviceId,
            nextHopId = neighbor.deviceId,
            hopCount = neighbor.hopCount,
            score = score(neighbor),
            lastUpdated = neighbor.lastSeen
        )
        val existing = routes[neighbor.deviceId]
        if (existing == null || route.score > existing.score) routes[neighbor.deviceId] = route
    }

    override suspend fun bestRoute(destinationId: String): Route? {
        val route = routes[destinationId] ?: return null
        return route.takeIf { System.currentTimeMillis() - it.lastUpdated <= routeTimeoutMs }
    }

    override suspend fun forward(packet: MeshPacket): Result<Unit> {
        if (packet.expired()) return Result.failure(IllegalStateException("Packet expired"))
        val route = bestRoute(packet.destinationId)
            ?: return Result.failure(IllegalStateException("No route to ${packet.destinationId}"))
        if (route.nextHopId == packet.previousHop) {
            return Result.failure(IllegalStateException("Loop prevention rejected previous hop"))
        }
        return forwarder.send(route.nextHopId, packet.forwarded(previous = route.nextHopId, next = route.nextHopId))
    }

    override suspend fun removeExpiredRoutes(now: Long) {
        routes.entries.forEach { entry ->
            if (now - entry.value.lastUpdated > routeTimeoutMs) routes.remove(entry.key, entry.value)
        }
        neighbors.entries.forEach { entry ->
            if (now - entry.value.lastSeen > routeTimeoutMs) neighbors.remove(entry.key, entry.value)
        }
    }

    private fun score(neighbor: Neighbor): Double {
        val signal = ((neighbor.rssi + 100).coerceIn(0, 100)).toDouble()
        val battery = (neighbor.batteryPercent ?: 50).coerceIn(0, 100) * 0.2
        val latency = (100 - ((neighbor.latencyMs ?: 1000L) / 10).coerceAtMost(100)).toDouble() * 0.2
        val hops = (20 - neighbor.hopCount.coerceAtMost(20)).toDouble() * 2
        return signal + battery + latency + hops
    }
}
