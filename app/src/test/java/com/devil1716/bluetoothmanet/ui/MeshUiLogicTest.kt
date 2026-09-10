package com.devil1716.bluetoothmanet.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshUiLogicTest {
    @Test
    fun generatedNodeIdIsUniquePerSeedAndNotLegacyA() {
        val first = generateDefaultNodeId("android-id-phone-1")
        val second = generateDefaultNodeId("android-id-phone-2")
        assertEquals(4, first.length)
        assertEquals(4, second.length)
        assertNotEquals(first, second)
        assertNotEquals(LEGACY_DEFAULT_NODE_ID, first)
        assertNotEquals(LEGACY_DEFAULT_NODE_ID, second)
        assertTrue(first.all { it.isLetterOrDigit() })
        assertEquals(first, generateDefaultNodeId("android-id-phone-1"))
    }

    @Test
    fun blankStoredNodeIdIsTreatedAsMissing() {
        assertEquals(null, resolveInitialNodeId(null))
        assertEquals(null, resolveInitialNodeId("  "))
        assertEquals("K7M2", resolveInitialNodeId("k7m2"))
    }

    @Test
    fun meshStatusChipShowsIdleUntilStarted() {
        assertEquals("Idle", meshStatusChipLabel(false, emptyList()))
        assertEquals("0 links", meshStatusChipLabel(true, emptyList()))
        assertEquals("1 link · B4Q1", meshStatusChipLabel(true, listOf("B4Q1")))
        assertEquals(
            "2 links · B4Q1 · M8Z2",
            meshStatusChipLabel(true, listOf("B4Q1", "M8Z2"))
        )
    }

    @Test
    fun peerLabelsStripMacSuffix() {
        assertEquals(
            listOf("B4Q1", "M8Z2"),
            parsePeerNodeIds(listOf("B4Q1 (AA:BB:CC:DD:EE:FF)", "M8Z2", "B4Q1 (AA:BB:CC:DD:EE:FF)"))
        )
    }

    @Test
    fun byteSizeFormatting() {
        assertEquals("512 B", formatByteSize(512))
        assertEquals("1.5 KB", formatByteSize(1536))
        assertEquals("1.0 MB", formatByteSize(1024L * 1024L))
    }

    @Test
    fun fileProgressBecomesVisibleSuccessAndFailure() {
        val sending = fileTransferFromProgress("notes.pdf", 2, 10)
        assertEquals(FileTransferPhase.SENDING, sending.phase)
        assertEquals("notes.pdf", sending.fileName)
        assertEquals(0.2f, sending.progress, 0.001f)
        assertTrue(sending.visible)

        val done = fileTransferFromProgress("notes.pdf", 10, 10)
        assertEquals(FileTransferPhase.SUCCESS, done.phase)

        val failed = fileTransferFromLog("File send failed. Start mesh, wait for a signed link, then try again.", sending)
        assertEquals(FileTransferPhase.FAILED, failed.phase)
        assertFalse(failed.error.isBlank())
    }

    @Test
    fun firstRunChecklistStaysUntilMeshAndPermissionsAreReady() {
        assertTrue(shouldShowFirstRunChecklist(0, false, false, true))
        assertTrue(shouldShowFirstRunChecklist(0, true, true, true))
        assertTrue(shouldShowFirstRunChecklist(3, false, true, true))
        assertFalse(shouldShowFirstRunChecklist(2, true, true, true))
    }
}
