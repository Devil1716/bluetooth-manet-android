package com.devil1716.bluetoothmanet.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshUiStateTest {
    private fun peer(
        id: String,
        nearby: Boolean = false,
        hasMessages: Boolean = false,
        timestamp: Long = 0L,
        online: Boolean = false,
        pendingRequest: Boolean = false
    ) = ConversationPreview(
        id = id,
        name = id,
        preview = "",
        timestamp = timestamp,
        online = online,
        hasAttachment = false,
        hasMessages = hasMessages,
        nearby = nearby,
        pendingRequest = pendingRequest
    )

    @Test
    fun nearbyTabOnlyListsPeersInRadioRange() {
        val state = MeshUiState(
            meshStarted = true,
            conversations = listOf(
                peer("INRANGE", nearby = true),
                peer("OLDCHAT", hasMessages = true, timestamp = 500L),
                peer("BOTH", nearby = true, hasMessages = true, timestamp = 900L)
            )
        )

        assertEquals(listOf("INRANGE", "BOTH"), state.nearbyPeople.map { it.id })
    }

    @Test
    fun nearbyTabEmptiesWhenTheMeshIsTurnedOff() {
        // Presence rows stay fresh for a while after the radio stops; showing
        // them would offer chats to people who can no longer be reached.
        val state = MeshUiState(
            meshStarted = false,
            conversations = listOf(peer("INRANGE", nearby = true))
        )

        assertTrue(state.nearbyPeople.isEmpty())
    }

    @Test
    fun chatsTabOnlyListsThreadsThatHaveMessages() {
        val state = MeshUiState(
            conversations = listOf(
                peer("INRANGE", nearby = true),
                peer("OLDER", hasMessages = true, timestamp = 500L),
                peer("NEWER", hasMessages = true, timestamp = 900L)
            )
        )

        assertEquals(listOf("NEWER", "OLDER"), state.chatThreads.map { it.id })
    }

    @Test
    fun aPeerWithNoHistoryStillOpensAThread() {
        val state = MeshUiState(openConversationId = "K7M2")

        assertEquals("K7M2", state.openConversation?.id)
    }

    @Test
    fun searchNarrowsChatsWithoutTouchingTheNearbyRoster() {
        val state = MeshUiState(
            searchQuery = "new",
            meshStarted = true,
            conversations = listOf(
                peer("INRANGE", nearby = true),
                peer("NEWER", hasMessages = true, timestamp = 900L),
                peer("OLDER", hasMessages = true, timestamp = 500L)
            )
        )

        assertEquals(listOf("NEWER"), state.chatThreads.map { it.id })
        assertTrue("Nearby must not be filtered by the chat search box",
            state.nearbyPeople.map { it.id }.contains("INRANGE"))
    }

    @Test
    fun pendingRequestsStayOutOfTheInbox() {
        val state = MeshUiState(
            conversations = listOf(
                peer("FRIEND", hasMessages = true, timestamp = 500L),
                peer("STRANGER", hasMessages = true, timestamp = 900L, pendingRequest = true)
            )
        )

        assertEquals(listOf("FRIEND"), state.chatThreads.map { it.id })
        assertEquals(listOf("STRANGER"), state.messageRequests.map { it.id })
        assertEquals(1, state.requestCount)
    }

    @Test
    fun requestsAreListedNewestFirstAndIgnoreTheChatSearchBox() {
        val state = MeshUiState(
            searchQuery = "friend",
            conversations = listOf(
                peer("OLDREQ", hasMessages = true, timestamp = 100L, pendingRequest = true),
                peer("NEWREQ", hasMessages = true, timestamp = 800L, pendingRequest = true)
            )
        )

        assertEquals(listOf("NEWREQ", "OLDREQ"), state.messageRequests.map { it.id })
    }

    @Test
    fun theOpenThreadReportsWhetherItIsStillGated() {
        val gated = MeshUiState(
            openConversationId = "STRANGER",
            conversations = listOf(peer("STRANGER", hasMessages = true, pendingRequest = true))
        )
        val accepted = MeshUiState(
            openConversationId = "FRIEND",
            conversations = listOf(peer("FRIEND", hasMessages = true))
        )

        assertTrue(gated.openConversationIsRequest)
        assertTrue(!accepted.openConversationIsRequest)
    }

    @Test
    fun distanceLabelFallsBackWhenSignalStrengthIsUnknown() {
        // An unlinked peer can be in the roster before any RSSI is recorded.
        assertEquals("Nearby", formatDistanceLabel(rssi = 0, hopCount = 1))
        assertEquals("~ 80 m away", formatDistanceLabel(rssi = 0, hopCount = 2))
        assertTrue(formatDistanceLabel(rssi = -55, hopCount = 1).endsWith("m away"))
    }
}
