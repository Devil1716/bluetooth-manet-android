package com.devil1716.bluetoothmanet.ui

import java.util.Locale

/** Whether a peer may reach the inbox, or is still waiting to be let in. */
enum class ConversationGate { ACCEPTED, PENDING_REQUEST }

/**
 * Decides whether a conversation belongs in the inbox or in Message requests.
 *
 * Replying is treated as consent, so a thread the user has already spoken in
 * never reappears as a request. A peer with nothing said yet is not a request
 * either: that is someone discovered in Nearby, and the user tapping them is
 * how a conversation begins.
 */
fun conversationGate(
    accepted: Boolean,
    hasOutgoing: Boolean,
    hasIncoming: Boolean
): ConversationGate = when {
    accepted || hasOutgoing -> ConversationGate.ACCEPTED
    hasIncoming -> ConversationGate.PENDING_REQUEST
    else -> ConversationGate.ACCEPTED
}

fun normalizePeerId(raw: String?): String = raw?.trim()?.uppercase(Locale.US).orEmpty()

/** Title for the requests entry point, e.g. "3 message requests". */
fun messageRequestsTitle(count: Int): String = when {
    count <= 0 -> "Message requests"
    count == 1 -> "1 message request"
    else -> "$count message requests"
}

/** Sub-line naming who is waiting, so the user can triage without tapping. */
fun messageRequestsSubtitle(names: List<String>): String {
    val people = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return when (people.size) {
        0 -> "No one is waiting"
        1 -> "${people[0]} wants to chat"
        2 -> "${people[0]} and ${people[1]} want to chat"
        else -> "${people[0]}, ${people[1]} and ${people.size - 2} more want to chat"
    }
}

fun requestPrompt(name: String): String {
    val who = name.trim().ifEmpty { "This phone" }
    return "$who wants to chat. Accept to reply, or delete the request."
}

/** Copy for the requests screen when nothing is waiting. */
fun requestsEmptyBody(): String =
    "When someone nearby messages you for the first time, it waits here until you accept."
