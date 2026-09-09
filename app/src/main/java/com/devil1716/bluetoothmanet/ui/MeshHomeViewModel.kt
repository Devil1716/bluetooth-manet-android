package com.devil1716.bluetoothmanet.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.devil1716.bluetoothmanet.AppDatabase
import com.devil1716.bluetoothmanet.ChatMessageEntity
import com.devil1716.bluetoothmanet.MeshFileStore
import com.devil1716.bluetoothmanet.MeshNeighborEntity
import com.devil1716.bluetoothmanet.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MeshHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val prefs = application.getSharedPreferences("mesh", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(
        MeshUiState(nodeId = prefs.getString("node_id", "A") ?: "A")
    )
    val uiState: StateFlow<MeshUiState> = _uiState.asStateFlow()

    private var messages: List<ChatMessageEntity> = emptyList()
    private var neighbors: List<MeshNeighborEntity> = emptyList()
    private var livePeerLabels: List<String> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            val loadedMessages = withContext(Dispatchers.IO) { database.messageDao().getAll() }
            val loadedNeighbors = withContext(Dispatchers.IO) { database.meshStateDao().neighbors() }
            messages = loadedMessages
            neighbors = loadedNeighbors
            publish()
        }
    }

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun setNodeId(nodeId: String) {
        val trimmed = nodeId.trim().uppercase()
        prefs.edit().putString("node_id", trimmed).apply()
        _uiState.update { it.copy(nodeId = trimmed) }
    }

    fun setComposerText(text: String) = _uiState.update { it.copy(composerText = text) }

    fun setNewChatNodeId(nodeId: String) = _uiState.update { it.copy(newChatNodeId = nodeId) }

    fun openSetup() = _uiState.update { it.copy(sheetVisible = true) }

    fun closeSetup() = _uiState.update { it.copy(sheetVisible = false) }

    fun openThread(conversationId: String) {
        val id = conversationId.trim().uppercase()
        if (id.isEmpty()) return
        _uiState.update {
            it.copy(
                openConversationId = id,
                composerText = "",
                sheetVisible = false,
                newChatNodeId = ""
            )
        }
        publish()
    }

    fun closeThread() = _uiState.update { it.copy(openConversationId = null, composerText = "") }

    fun clearComposer() = _uiState.update { it.copy(composerText = "") }

    fun setClassicPeers(peers: List<PeerDevice>) = _uiState.update { it.copy(classicPeers = peers) }

    fun setUpdateStatus(status: String, busy: Boolean? = null) {
        _uiState.update {
            it.copy(
                updateStatus = status,
                updateBusy = busy ?: it.updateBusy
            )
        }
        if (status.isNotBlank()) appendLog(status)
    }

    fun onMeshStatus(message: String?, peers: List<String>?, fileProgress: String?) {
        if (peers != null) livePeerLabels = peers
        _uiState.update { state ->
            state.copy(
                meshStatus = when {
                    peers == null -> message ?: state.meshStatus
                    peers.isEmpty() -> "Mesh idle"
                    else -> "${peers.size} signed link(s)"
                },
                connectionsLabel = when {
                    peers == null -> state.connectionsLabel
                    peers.isEmpty() -> "No mesh links yet. Tap Start Mesh on both phones."
                    else -> peers.joinToString("\n")
                },
                fileProgress = fileProgress ?: state.fileProgress,
                logs = if (message.isNullOrBlank()) state.logs else state.logs + "Mesh: $message\n"
            )
        }
        if (peers != null || fileProgress != null) refresh()
    }

    fun appendLog(message: String) {
        _uiState.update { it.copy(logs = it.logs + message + "\n") }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    private fun publish() {
        val onlineNodes = onlineNodeIds()
        val grouped = messages.groupBy { it.conversationId }
        val fromMessages = grouped.map { (id, items) ->
            val last = items.maxByOrNull { it.timestamp } ?: return@map null
            ConversationPreview(
                id = id,
                name = displayNameFor(id),
                preview = MeshFileStore.displayName(last.text),
                timestamp = last.timestamp,
                online = isOnline(id, onlineNodes),
                hasAttachment = MeshFileStore.isFileMessage(last.text)
            )
        }.filterNotNull()
        val seen = fromMessages.map { it.id.uppercase() }.toHashSet()
        val fromNeighbors = neighbors.mapNotNull { neighbor ->
            val node = neighbor.displayName?.trim().orEmpty()
            if (node.isEmpty() || !seen.add(node.uppercase())) null
            else ConversationPreview(
                id = node,
                name = displayNameFor(node),
                preview = "Tap to start a conversation",
                timestamp = if (neighbor.lastSeen > 0L) neighbor.lastSeen else 0L,
                online = neighbor.connected || isOnline(node, onlineNodes),
                hasAttachment = false
            )
        }
        val conversations = (fromMessages + fromNeighbors)
            .sortedWith(compareByDescending<ConversationPreview> { it.online }.thenByDescending { it.timestamp })
        val stories = buildStories(conversations, onlineNodes)
        val openId = _uiState.value.openConversationId
        val thread = if (openId == null) emptyList()
        else messages.filter { it.conversationId.equals(openId, true) }.sortedBy { it.timestamp }
        _uiState.update {
            it.copy(
                conversations = conversations,
                stories = stories,
                threadMessages = thread
            )
        }
    }

    private fun buildStories(
        conversations: List<ConversationPreview>,
        onlineNodes: Set<String>
    ): List<StoryPeer> {
        val stories = LinkedHashMap<String, StoryPeer>()
        neighbors.sortedByDescending { it.connected }.forEach { neighbor ->
            val node = neighbor.displayName?.trim().orEmpty()
            if (node.isNotEmpty()) {
                stories[node.uppercase()] = StoryPeer(
                    id = node,
                    name = firstName(displayNameFor(node)),
                    online = neighbor.connected || isOnline(node, onlineNodes)
                )
            }
        }
        conversations.forEach { conversation ->
            stories.putIfAbsent(
                conversation.id.uppercase(),
                StoryPeer(
                    id = conversation.id,
                    name = firstName(conversation.name),
                    online = conversation.online
                )
            )
        }
        return stories.values.toList()
    }

    private fun displayNameFor(id: String): String {
        val match = neighbors.firstOrNull {
            it.displayName.equals(id, true) || it.deviceId.equals(id, true)
        }
        val label = match?.displayName?.trim().orEmpty()
        return label.ifEmpty { id }
    }

    private fun onlineNodeIds(): Set<String> {
        val ids = HashSet<String>()
        neighbors.filter { it.connected }.forEach { neighbor ->
            neighbor.displayName?.trim()?.uppercase()?.let { if (it.isNotEmpty()) ids.add(it) }
            ids.add(neighbor.deviceId.uppercase())
        }
        livePeerLabels.forEach { label ->
            val node = label.substringBefore(" (").trim()
            if (node.isNotEmpty()) ids.add(node.uppercase())
        }
        return ids
    }

    private fun isOnline(id: String, onlineNodes: Set<String>): Boolean {
        val key = id.trim().uppercase()
        if (key in onlineNodes) return true
        return neighbors.any { neighbor ->
            neighbor.connected && (
                neighbor.displayName.equals(id, true) ||
                    neighbor.deviceId.equals(id, true)
                )
        }
    }
}
