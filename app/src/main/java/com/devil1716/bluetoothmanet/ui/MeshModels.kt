package com.devil1716.bluetoothmanet.ui

import androidx.compose.ui.graphics.Color
import com.devil1716.bluetoothmanet.ChatMessageEntity
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.update.UpdateUi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ConversationPreview(
    val id: String,
    val name: String,
    val preview: String,
    val timestamp: Long,
    val online: Boolean,
    val hasAttachment: Boolean
)

data class StoryPeer(
    val id: String,
    val name: String,
    val online: Boolean
)

enum class FileTransferPhase {
    NONE,
    SENDING,
    SUCCESS,
    FAILED
}

data class FileTransferUi(
    val phase: FileTransferPhase = FileTransferPhase.NONE,
    val fileName: String = "",
    val sizeLabel: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val error: String = ""
) {
    val progress: Float
        get() = if (total > 0) (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val visible: Boolean
        get() = phase != FileTransferPhase.NONE
}

data class PermissionUi(
    val allGranted: Boolean = false,
    val missingCount: Int = 0,
    val permanentlyDenied: Boolean = false,
    val locationServicesOff: Boolean = false,
    val bluetoothOff: Boolean = false,
    val showRationale: Boolean = true
) {
    val readyForMesh: Boolean get() = allGranted && !bluetoothOff
}

data class MeshUiState(
    val nodeId: String = "",
    val searchQuery: String = "",
    val conversations: List<ConversationPreview> = emptyList(),
    val stories: List<StoryPeer> = emptyList(),
    val threadMessages: List<ChatMessageEntity> = emptyList(),
    val openConversationId: String? = null,
    val settingsVisible: Boolean = false,
    val newChatVisible: Boolean = false,
    val showWelcome: Boolean = false,
    val onboardingComplete: Boolean = false,
    val meshStarted: Boolean = false,
    val meshStatus: String = "Offline",
    val statusChip: String = "Offline",
    val livePeerIds: List<String> = emptyList(),
    val connectionsLabel: String = "Nearby chat is off",
    val fileProgress: String = "No file transfer",
    val fileTransfer: FileTransferUi = FileTransferUi(),
    val logs: String = "",
    val classicPeers: List<PeerDevice> = emptyList(),
    val composerText: String = "",
    val newChatNodeId: String = "",
    val update: UpdateUi = UpdateUi(),
    val permission: PermissionUi = PermissionUi(),
    val nodeIdCopied: Boolean = false
) {
    val updateStatus: String get() = update.message
    val updateBusy: Boolean get() = update.busy
    val openConversation: ConversationPreview?
        get() = openConversationId?.let { id -> conversations.firstOrNull { it.id.equals(id, true) } }
            ?: openConversationId?.let { ConversationPreview(it, it, "", 0L, false, false) }

    val filteredConversations: List<ConversationPreview>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return conversations
            return conversations.filter {
                it.name.contains(query, true) || it.preview.contains(query, true) || it.id.contains(query, true)
            }
        }
}

fun formatInboxTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return ""
    val diff = now - timestamp
    if (diff < 60_000L) return "Just Now"
    val nowCal = Calendar.getInstance().apply { timeInMillis = now }
    val thenCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    if (nowCal.get(Calendar.YEAR) == thenCal.get(Calendar.YEAR)
        && nowCal.get(Calendar.DAY_OF_YEAR) == thenCal.get(Calendar.DAY_OF_YEAR)
    ) {
        return SimpleDateFormat("h:mm a", Locale.US).format(Date(timestamp)).lowercase(Locale.US)
    }
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -1)
    }
    if (yesterday.get(Calendar.YEAR) == thenCal.get(Calendar.YEAR)
        && yesterday.get(Calendar.DAY_OF_YEAR) == thenCal.get(Calendar.DAY_OF_YEAR)
    ) {
        return "Yesterday"
    }
    return SimpleDateFormat("MMM. d", Locale.US).format(Date(timestamp))
}

fun avatarInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.US)
        parts.isNotEmpty() -> parts[0].take(2).uppercase(Locale.US)
        else -> "?"
    }
}

fun avatarColor(key: String): Color {
    val palette = listOf(
        Color(0xFF6C5CE7),
        Color(0xFF00CEC9),
        Color(0xFFFD79A8),
        Color(0xFFFDCB6E),
        Color(0xFF74B9FF),
        Color(0xFF55EFC4),
        Color(0xFFA29BFE),
        Color(0xFFE17055)
    )
    val index = (key.uppercase(Locale.US).hashCode() and 0x7FFFFFFF) % palette.size
    return palette[index]
}

fun firstName(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifEmpty { name }
