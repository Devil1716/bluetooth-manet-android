package com.devil1716.bluetoothmanet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshDiagnosticsTest {
    @Test
    fun redactsWirePayloadsAndLongHex() {
        assertEquals("redacted", MeshDiagnostics.sanitizeDetail("MSG|id|A|B|7|secret-body|sig"))
        assertEquals("redacted", MeshDiagnostics.sanitizeDetail("a".repeat(32)))
        assertEquals("bluetooth_off", MeshDiagnostics.sanitizeDetail("bluetooth_off"))
    }

    @Test
    fun ringDropsOldestAndNeverStoresPayloads() {
        MeshDiagnostics.clear()
        repeat(MeshDiagnostics.MAX_EVENTS + 5) { index ->
            MeshDiagnostics.record("conn", "peer_change", "count=$index")
        }
        val snap = MeshDiagnostics.snapshot()
        assertEquals(MeshDiagnostics.MAX_EVENTS, snap.size)
        assertTrue(snap.first().detail.contains("count=5"))
        MeshDiagnostics.record("msg", "received", "MSG|id|A|B|7|hello")
        assertEquals("redacted", MeshDiagnostics.snapshot().last().detail)
        MeshDiagnostics.clear()
    }
}
