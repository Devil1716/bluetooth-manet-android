package com.devil1716.bluetoothmanet.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.devil1716.bluetoothmanet.AcceptedPeerEntity
import com.devil1716.bluetoothmanet.AppDatabase
import com.devil1716.bluetoothmanet.ChatMessageEntity
import com.devil1716.bluetoothmanet.MeshFileStore
import com.devil1716.bluetoothmanet.MeshNeighborEntity
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.update.UpdatePhase
import com.devil1716.bluetoothmanet.update.UpdateUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
            showWelcome = false,
            onboardingComplete = prefs.getBoolean("onboarding_complete", false) ||
                prefs.getBoolean("onboarded", false),
            onboarded = prefs.getBoolean("onboarded", false) ||
                prefs.getBoolean("onboarding_complete", false)
        )
    )
    val uiState: StateFlow<MeshUiState> = _uiState.asStateFlow()

    private var messages: List<ChatMessageEntity> = emptyList()
    private var neighbors: List<MeshNeighborEntity> = emptyList()
    private var livePeerLabels: List<String> = emptyList()
    private var acceptedPeers: Set<String> = emptySet()

    /**
     * Peers accepted in this session whose database write may still be in
     * flight. Unioned into every reload so a refresh landing mid-write cannot
     * bounce a peer back into requests.
     */
    private val optimisticAccepts = mutableSetOf<String>()
    private var presenceTicker: Job? = null

    init {
        refresh()
        startPresenceTicker()
    }

    fun refresh() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Triple(
                    database.messageDao().getAll(),
                    database.meshStateDao().recentNeighbors(System.currentTimeMillis() - NEARBY_TTL_MS),
                    database.conversationRequestDao().acceptedPeerIds()
                        .mapTo(HashSet()) { normalizePeerId(it) }
                )
            }
            messages = loaded.first
            neighbors = loaded.second
            acceptedPeers = loaded.third + optimisticAccepts
            publish()
        }
    }

    /**
     * Presence decays with wall-clock time, so the Nearby list has to be
     * rebuilt on a timer. Without this a peer that walked away stays on screen
     * until the next unrelated mesh broadcast happens to arrive.
     */
    private fun startPresenceTicker() {
        if (presenceTicker?.isActive == true) return
        presenceTicker = scope.launch {
            while (true) {
                delay(PRESENCE_TICK_MS)
                refresh()
            }
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

    fun markMeshStopped() {
        _uiState.update { state ->
            state.copy(
                meshStarted = false,
                livePeerIds = emptyList(),
                meshStatus = meshStatusChipLabel(false, emptyList()),
                statusChip = meshStatusChipLabel(false, emptyList()),
                connectionsLabel = humanConnectionsLabel(false, emptyList())
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

    fun setComposerText(text: String) {
        _uiState.update { state ->
            val id = state.openConversationId
            if (!id.isNullOrBlank()) {
                prefs.edit().putString(draftKey(id), text).apply()
            }
            state.copy(composerText = text)
        }
    }

    fun setNewChatNodeId(nodeId: String) = _uiState.update { it.copy(newChatNodeId = nodeId) }

    fun openSetup() = openSettings()

    fun closeSetup() = closeSettings()

    fun completeOnboarding() {
        prefs.edit()
            .putBoolean("onboarded", true)
            .putBoolean("onboarding_complete", true)
            .putBoolean("onboarding_welcome_seen", true)
            .apply()
        _uiState.update {
            it.copy(onboarded = true, onboardingComplete = true, showWelcome = false)
        }
    }

    fun setHomeTab(tab: MeshHomeTab) = _uiState.update { it.copy(homeTab = tab) }

    fun openThread(conversationId: String) {
        val id = conversationId.trim().uppercase()
        if (id.isEmpty()) return
        val draft = prefs.getString(draftKey(id), "").orEmpty()
        _uiState.update {
            it.copy(
                openConversationId = id,
                composerText = draft,
                settingsVisible = false,
                newChatVisible = false,
                newChatNodeId = ""
            )
        }
        publish()
    }

    fun closeThread() = _uiState.update { it.copy(openConversationId = null, composerText = "") }

    fun openRequests() = _uiState.update {
        it.copy(requestsVisible = true, settingsVisible = false, newChatVisible = false)
    }

    fun closeRequests() = _uiState.update { it.copy(requestsVisible = false) }

    /**
     * Lets a peer into the inbox. The state flips immediately so the request
     * row can animate away without waiting for the database round trip, and
     * the thread is opened so the user can reply straight away.
     */
    fun acceptRequest(conversationId: String, thenOpenThread: Boolean = true) {
        val id = normalizePeerId(conversationId)
        if (id.isEmpty()) return
        optimisticAccepts += id
        acceptedPeers = acceptedPeers + id
        persistAcceptance(id)
        publish()
        if (thenOpenThread) {
            openThread(id)
            _uiState.update { it.copy(requestsVisible = false) }
        } else {
            _uiState.update { state ->
                // Leave the requests screen once the queue is empty.
                if (state.requestCount == 0) state.copy(requestsVisible = false) else state
            }
        }
    }

    /**
     * Declines a request by discarding the thread. No "declined" flag is kept,
     * so if that peer messages again it simply raises a fresh request instead
     * of being silently swallowed.
     */
    fun declineRequest(conversationId: String) {
        val id = normalizePeerId(conversationId)
        if (id.isEmpty()) return
        optimisticAccepts -= id
        acceptedPeers = acceptedPeers - id
        messages = messages.filterNot { normalizePeerId(it.conversationId) == id }
        prefs.edit().remove(draftKey(id)).apply()
        _uiState.update { state ->
            if (state.openConversationId.equals(id, true)) {
                state.copy(openConversationId = null, composerText = "")
            } else {
                state
            }
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                database.conversationRequestDao().forget(id)
                database.messageDao().deleteConversation(id)
            }
            refresh()
        }
        publish()
        _uiState.update { state ->
            if (state.requestCount == 0) state.copy(requestsVisible = false) else state
        }
    }

    /** Sending to someone is consent to hear back from them. */
    fun noteOutgoingTo(conversationId: String) {
        val id = normalizePeerId(conversationId)
        if (id.isEmpty() || id in acceptedPeers) return
        optimisticAccepts += id
        acceptedPeers = acceptedPeers + id
        persistAcceptance(id)
        publish()
    }

    private fun persistAcceptance(peerId: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.conversationRequestDao()
                    .accept(AcceptedPeerEntity(peerId, System.currentTimeMillis()))
            }
        }
    }

    fun clearComposer() {
        val id = _uiState.value.openConversationId
        if (!id.isNullOrBlank()) prefs.edit().remove(draftKey(id)).apply()
        _uiState.update { it.copy(composerText = "") }
    }

    private fun draftKey(conversationId: String) = "draft_" + conversationId.trim().uppercase()

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

    fun noteIdentityConflict(nodeId: String, fingerprint: String) {
        val id = nodeId.trim()
        if (id.isEmpty()) return
        _uiState.update {
            it.copy(identityConflicts = it.identityConflicts + (id.uppercase() to fingerprint))
        }
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

    /**
     * The radio found or lost nearby phones. Seeing anyone at all proves the
     * mesh is live, which lets the UI leave the "Connecting" state even before
     * a GATT link finishes negotiating.
     */
    fun onNearbyRosterChanged(nearbyCount: Int) {
        if (nearbyCount > 0) {
            _uiState.update { state ->
                if (state.meshStarted) state else state.copy(
                    meshStarted = true,
                    onboardingComplete = true
                )
            }
        }
        refresh()
    }

    fun appendLog(message: String) {
        val line = message.take(120)
        _uiState.update {
            val next = it.logs + line + "\n"
            it.copy(logs = if (next.length > 8_000) next.takeLast(6_000) else next)
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        val onlineNodes = onlineNodeIds(now)
        // Group case-insensitively: outbound rows are uppercased but inbound
        // node IDs arrive verbatim off the wire, and grouping on the raw value
        // splits one peer into two threads.
        val grouped = messages.groupBy { it.conversationId.trim().uppercase() }
        val fromMessages = grouped.mapNotNull { (id, items) ->
            val last = items.maxByOrNull { it.timestamp } ?: return@mapNotNull null
            val neighbor = neighborFor(id)
            val online = isOnline(id, onlineNodes, now)
            val nearby = isNearby(neighbor, now)
            val gate = conversationGate(
                accepted = id in acceptedPeers,
                hasOutgoing = items.any { it.sentByMe },
                hasIncoming = items.any { !it.sentByMe }
            )
            ConversationPreview(
                id = id,
                name = displayNameFor(id),
                preview = MeshFileStore.displayName(last.text),
                timestamp = last.timestamp,
                online = online,
                hasAttachment = MeshFileStore.isFileMessage(last.text),
                rssi = neighbor?.rssi ?: 0,
                hopCount = neighbor?.hopCount ?: 1,
                subtitle = nearbySubtitle(online, neighbor?.hopCount ?: 1),
                distanceLabel = formatDistanceLabel(neighbor?.rssi ?: 0, neighbor?.hopCount ?: 1),
                hasMessages = true,
                nearby = nearby,
                pendingRequest = gate == ConversationGate.PENDING_REQUEST
            )
        }
        val seen = fromMessages.map { it.id.uppercase() }.toHashSet()
        val fromNeighbors = neighbors.mapNotNull { neighbor ->
            val node = neighbor.displayName?.trim()?.uppercase().orEmpty()
            if (node.isEmpty() || !seen.add(node)) return@mapNotNull null
            val online = isOnline(node, onlineNodes, now)
            ConversationPreview(
                id = node,
                name = displayNameFor(node),
                preview = "Tap to start a conversation",
                timestamp = if (neighbor.lastSeen > 0L) neighbor.lastSeen else 0L,
                online = online,
                hasAttachment = false,
                rssi = neighbor.rssi,
                hopCount = neighbor.hopCount,
                subtitle = nearbySubtitle(online, neighbor.hopCount),
                distanceLabel = formatDistanceLabel(neighbor.rssi, neighbor.hopCount),
                nearby = isNearby(neighbor, now)
            )
        }
        val conversations = (fromMessages + fromNeighbors)
            .sortedWith(
                compareByDescending<ConversationPreview> { it.online }
                    .thenByDescending { it.nearby }
                    .thenByDescending { it.timestamp }
            )
        if (conversations.isNotEmpty() && !prefs.getBoolean("onboarding_complete", false)) {
            prefs.edit().putBoolean("onboarding_complete", true).apply()
        }
        val stories = buildStories(conversations, onlineNodes, now)
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

    /** A neighbour counts as nearby only while its last sighting is fresh. */
    private fun isNearby(neighbor: MeshNeighborEntity?, now: Long): Boolean {
        if (neighbor == null) return false
        if (neighbor.lastSeen <= 0L) return false
        return now - neighbor.lastSeen <= NEARBY_TTL_MS
    }

    private fun buildStories(
        conversations: List<ConversationPreview>,
        onlineNodes: Set<String>,
        now: Long
    ): List<StoryPeer> {
        val stories = LinkedHashMap<String, StoryPeer>()
        neighbors.sortedByDescending { it.connected }.forEach { neighbor ->
            val node = neighbor.displayName?.trim()?.uppercase().orEmpty()
            if (node.isNotEmpty()) {
                stories[node] = StoryPeer(
                    id = node,
                    name = firstName(displayNameFor(node)),
                    online = isOnline(node, onlineNodes, now)
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

    private fun neighborFor(id: String): MeshNeighborEntity? {
        return neighbors.firstOrNull {
            it.displayName.equals(id, true) || it.deviceId.equals(id, true)
        }
    }

    private fun displayNameFor(id: String): String {
        val match = neighbors.firstOrNull {
            it.displayName.equals(id, true) || it.deviceId.equals(id, true)
        }
        val label = match?.displayName?.trim().orEmpty()
        return label.ifEmpty { id }
    }

    /**
     * Node IDs reachable right now. A stored `connected` flag is not enough on
     * its own: the row survives the peer walking away, so freshness decides.
     */
    private fun onlineNodeIds(now: Long): Set<String> {
        val ids = HashSet<String>()
        neighbors.filter { it.connected && now - it.lastSeen <= NEARBY_TTL_MS }.forEach { neighbor ->
            neighbor.displayName?.trim()?.uppercase()?.let { if (it.isNotEmpty()) ids.add(it) }
            ids.add(neighbor.deviceId.uppercase())
        }
        // Live transport labels are authoritative but carry placeholders for
        // peers whose handshake has not finished; parsePeerNodeIds drops those.
        ids.addAll(parsePeerNodeIds(livePeerLabels))
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

    private fun isOnline(id: String, onlineNodes: Set<String>, now: Long): Boolean {
        val key = id.trim().uppercase()
        if (key.isEmpty()) return false
        if (key in onlineNodes) return true
        return neighbors.any { neighbor ->
            neighbor.connected &&
                now - neighbor.lastSeen <= NEARBY_TTL_MS &&
                (neighbor.displayName.equals(id, true) || neighbor.deviceId.equals(id, true))
        }
    }

    private companion object {
        /** Presence window; must stay above the radio's advertise interval. */
        const val NEARBY_TTL_MS = 60_000L
        const val PRESENCE_TICK_MS = 5_000L
    }
}
