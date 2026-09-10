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
    fun meshStatusChipUsesHumanNearbyCopy() {
        assertEquals("Offline", meshStatusChipLabel(false, emptyList()))
        assertEquals("Connecting", meshStatusChipLabel(true, emptyList()))
        assertEquals("Connected · 1 nearby", meshStatusChipLabel(true, listOf("B4Q1")))
        assertEquals("Connected · 2 nearby", meshStatusChipLabel(true, listOf("B4Q1", "M8Z2")))
        assertEquals("Nearby chat is off", humanConnectionsLabel(false, emptyList()))
        assertEquals("Looking for phones around you…", humanConnectionsLabel(true, emptyList()))
        assertEquals("B4Q1 · M8Z2", humanConnectionsLabel(true, listOf("B4Q1", "M8Z2")))
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
    fun getStartedHidesOnceMeshHasWorked() {
        assertTrue(shouldShowGetStarted(false, false, false, true))
        assertTrue(shouldShowGetStarted(false, true, false, true))
        assertFalse(shouldShowGetStarted(false, true, true, true))
        assertFalse(shouldShowGetStarted(true, false, false, true))
        assertFalse(shouldShowFirstRunChecklist(2, true, true, true))
        assertFalse(shouldShowFirstRunChecklist(0, true, true, true))
        assertTrue(shouldShowFirstRunChecklist(0, false, true, true))
    }

    @Test
    fun outgoingReceiptsUsePlainLanguage() {
        assertEquals("Sending", messageReceiptLabel(true, "SENDING"))
        assertEquals("Sent", messageReceiptLabel(true, "SENT"))
        assertEquals("Delivered", messageReceiptLabel(true, "DELIVERED"))
        assertEquals("Couldn't send", messageReceiptLabel(true, "FAILED"))
        assertEquals("", messageReceiptLabel(false, "DELIVERED"))
    }
}
