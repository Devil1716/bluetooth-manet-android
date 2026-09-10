package com.devil1716.bluetoothmanet.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.devil1716.bluetoothmanet.AppDatabase
import com.devil1716.bluetoothmanet.ChatMessageEntity
import com.devil1716.bluetoothmanet.MeshFileStore
import com.devil1716.bluetoothmanet.MeshNeighborEntity
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.update.UpdatePhase
import com.devil1716.bluetoothmanet.update.UpdateUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MeshHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val prefs = application.getSharedPreferences("mesh", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(
        MeshUiState(
            nodeId = resolveOrCreateNodeId(),
            showWelcome = !prefs.getBoolean("onboarding_welcome_seen", false),
            onboardingComplete = prefs.getBoolean("onboarding_complete", false)
        )
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
        _uiState.update { it.copy(nodeId = trimmed) }
        if (trimmed.isNotEmpty()) prefs.edit().putString("node_id", trimmed).apply()
    }

    fun markNodeIdCopied() {
        _uiState.update { it.copy(nodeIdCopied = true) }
        scope.launch {
            delay(2_000)
            _uiState.update { it.copy(nodeIdCopied = false) }
        }
    }

    fun setPermissionState(permission: PermissionUi) {
        _uiState.update { it.copy(permission = permission) }
    }

    fun markMeshStarted() {
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        _uiState.update { state ->
            state.copy(
                meshStarted = true,
                onboardingComplete = true,
                meshStatus = meshStatusChipLabel(true, state.livePeerIds),
                statusChip = meshStatusChipLabel(true, state.livePeerIds),
                connectionsLabel = humanConnectionsLabel(true, state.livePeerIds)
            )
        }
    }

    fun dismissWelcome() {
        prefs.edit().putBoolean("onboarding_welcome_seen", true).apply()
        _uiState.update { it.copy(showWelcome = false) }
    }

    fun openSettings() = _uiState.update { it.copy(settingsVisible = true, newChatVisible = false) }

    fun closeSettings() = _uiState.update { it.copy(settingsVisible = false) }

    fun openNewChat() = _uiState.update { it.copy(newChatVisible = true, newChatNodeId = "") }

    fun closeNewChat() = _uiState.update { it.copy(newChatVisible = false) }

    fun setFileTransfer(transfer: FileTransferUi) {
        _uiState.update {
            it.copy(
                fileTransfer = transfer,
                fileProgress = transfer.fileName.ifBlank { it.fileProgress }
            )
        }
    }

    fun setComposerText(text: String) = _uiState.update { it.copy(composerText = text) }

    fun setNewChatNodeId(nodeId: String) = _uiState.update { it.copy(newChatNodeId = nodeId) }

    fun openSetup() = openSettings()

    fun closeSetup() = closeSettings()

    fun openThread(conversationId: String) {
        val id = conversationId.trim().uppercase()
        if (id.isEmpty()) return
        _uiState.update {
            it.copy(
                openConversationId = id,
                composerText = "",
                settingsVisible = false,
                newChatVisible = false,
                newChatNodeId = ""
            )
        }
        publish()
    }

    fun closeThread() = _uiState.update { it.copy(openConversationId = null, composerText = "") }

    fun clearComposer() = _uiState.update { it.copy(composerText = "") }

    fun setClassicPeers(peers: List<PeerDevice>) = _uiState.update { it.copy(classicPeers = peers) }

    fun setUpdateStatus(status: String, busy: Boolean? = null) {
        _uiState.update { state ->
            val phase = when {
                busy == true -> UpdatePhase.CHECKING
                status.startsWith("Update failed") -> UpdatePhase.FAILED
                status.contains("different signing", ignoreCase = true) ->
                    UpdatePhase.SIGNATURE_CONFLICT
                else -> state.update.phase
            }
            state.copy(
                update = state.update.copy(
                    phase = phase,
                    message = status,
                )
            )
        }
        if (status.isNotBlank()) appendLog(status)
    }

    fun setUpdate(update: UpdateUi) {
        val previous = _uiState.value.update
        _uiState.update { it.copy(update = update) }
        if (update.message.isNotBlank() && update.phase != previous.phase) {
            appendLog(update.message)
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(update = it.update.copy(dismissed = true)) }
    }

    fun onMeshStatus(message: String?, peers: List<String>?, fileProgress: String?) {
        onMeshStatus(message, peers, fileProgress, null)
    }

    fun onMeshStatus(
        message: String?,
        peers: List<String>?,
        fileProgress: String?,
        transferUpdate: FileTransferUi?
    ) {
        if (peers != null) livePeerLabels = peers
        _uiState.update { state ->
            val peerIds = if (peers != null) parsePeerNodeIds(peers) else state.livePeerIds
            val started = state.meshStarted || !peerIds.isEmpty()
            val transfer = when {
                transferUpdate != null -> transferUpdate
                fileProgress != null -> fileTransferFromLog(fileProgress, state.fileTransfer)
                else -> fileTransferFromLog(message, state.fileTransfer)
            }
            state.copy(
                meshStarted = started,
                onboardingComplete = state.onboardingComplete || started,
                meshStatus = meshStatusChipLabel(started, peerIds),
                statusChip = meshStatusChipLabel(started, peerIds),
                livePeerIds = peerIds,
                connectionsLabel = humanConnectionsLabel(started, peerIds),
                fileProgress = fileProgress ?: state.fileProgress,
                fileTransfer = transfer,
                logs = if (message.isNullOrBlank()) state.logs else state.logs + "Mesh: $message\n"
            )
        }
        if (peers != null || fileProgress != null || transferUpdate != null) refresh()
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
                preview = "No messages yet",
                timestamp = if (neighbor.lastSeen > 0L) neighbor.lastSeen else 0L,
                online = neighbor.connected || isOnline(node, onlineNodes),
                hasAttachment = false
            )
        }
        val conversations = (fromMessages + fromNeighbors)
            .sortedWith(compareByDescending<ConversationPreview> { it.online }.thenByDescending { it.timestamp })
        if (conversations.isNotEmpty() && !prefs.getBoolean("onboarding_complete", false)) {
            prefs.edit().putBoolean("onboarding_complete", true).apply()
        }
        val stories = buildStories(conversations, onlineNodes)
        val openId = _uiState.value.openConversationId
        val thread = if (openId == null) emptyList()
        else messages.filter { it.conversationId.equals(openId, true) }.sortedBy { it.timestamp }
        _uiState.update {
            it.copy(
                conversations = conversations,
                stories = stories,
                threadMessages = thread,
                onboardingComplete = it.onboardingComplete || conversations.isNotEmpty()
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

    private fun resolveOrCreateNodeId(): String {
        val stored = resolveInitialNodeId(prefs.getString("node_id", null))
        if (stored != null) return stored
        val androidId = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val seed = androidId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val generated = generateDefaultNodeId(seed)
        prefs.edit().putString("node_id", generated).apply()
        return generated
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
