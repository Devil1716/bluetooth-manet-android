package com.devil1716.bluetoothmanet.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MeshRequestsTest {
    @Test
    fun aStrangerWhoMessagesFirstBecomesARequest() {
        assertEquals(
            ConversationGate.PENDING_REQUEST,
            conversationGate(accepted = false, hasOutgoing = false, hasIncoming = true)
        )
    }

    @Test
    fun anAcceptedPeerReachesTheInbox() {
        assertEquals(
            ConversationGate.ACCEPTED,
            conversationGate(accepted = true, hasOutgoing = false, hasIncoming = true)
        )
    }

    @Test
    fun replyingCountsAsConsentEvenWithoutAnAcceptRecord() {
        // Covers threads that predate the feature and the case where the user
        // starts the conversation from Nearby: neither should look like a request.
        assertEquals(
            ConversationGate.ACCEPTED,
            conversationGate(accepted = false, hasOutgoing = true, hasIncoming = true)
        )
    }

    @Test
    fun aNearbyPeerWithNothingSaidYetIsNotARequest() {
        assertEquals(
            ConversationGate.ACCEPTED,
            conversationGate(accepted = false, hasOutgoing = false, hasIncoming = false)
        )
    }

    @Test
    fun requestTitleCountsCorrectly() {
        assertEquals("Message requests", messageRequestsTitle(0))
        assertEquals("1 message request", messageRequestsTitle(1))
        assertEquals("4 message requests", messageRequestsTitle(4))
    }

    @Test
    fun requestSubtitleNamesWhoIsWaiting() {
        assertEquals("No one is waiting", messageRequestsSubtitle(emptyList()))
        assertEquals("K7M2 wants to chat", messageRequestsSubtitle(listOf("K7M2")))
        assertEquals(
            "K7M2 and B4Q1 want to chat",
            messageRequestsSubtitle(listOf("K7M2", "B4Q1"))
        )
        assertEquals(
            "K7M2, B4Q1 and 2 more want to chat",
            messageRequestsSubtitle(listOf("K7M2", "B4Q1", "M8Z2", "P3X9"))
        )
    }

    @Test
    fun requestSubtitleIgnoresBlanksAndDuplicates() {
        assertEquals(
            "K7M2 wants to chat",
            messageRequestsSubtitle(listOf("K7M2", " ", "K7M2"))
        )
    }

    @Test
    fun peerIdsNormalizeToOneCanonicalForm() {
        assertEquals("K7M2", normalizePeerId(" k7m2 "))
        assertEquals("", normalizePeerId(null))
        assertEquals("", normalizePeerId("   "))
    }

    @Test
    fun requestPromptFallsBackWhenTheNameIsMissing() {
        assertEquals(
            "K7M2 wants to chat. Accept to reply, or delete the request.",
            requestPrompt("K7M2")
        )
        assertEquals(
            "This phone wants to chat. Accept to reply, or delete the request.",
            requestPrompt("  ")
        )
    }
}
