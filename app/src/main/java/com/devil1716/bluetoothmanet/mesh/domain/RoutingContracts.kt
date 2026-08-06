package com.devil1716.bluetoothmanet.mesh.domain

data class Neighbor(
    val deviceId: String,
    val displayName: String,
    val rssi: Int,
    val batteryPercent: Int?,
    val latencyMs: Long?,
    val hopCount: Int,
    val lastSeen: Long,
    val connected: Boolean
)

data class Route(
    val destinationId: String,
    val nextHopId: String,
    val hopCount: Int,
    val score: Double,
    val lastUpdated: Long
)

interface RoutingManager {
    suspend fun updateNeighbor(neighbor: Neighbor)
    suspend fun bestRoute(destinationId: String): Route?
    suspend fun forward(packet: MeshPacket): Result<Unit>
    suspend fun removeExpiredRoutes(now: Long = System.currentTimeMillis())
}
