package com.devil1716.bluetoothmanet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.ui.screens.ChatThreadScreen
import com.devil1716.bluetoothmanet.ui.screens.ChatsHomeScreen
import com.devil1716.bluetoothmanet.ui.screens.MeshSetupSheet
import com.devil1716.bluetoothmanet.ui.theme.MeshTheme

data class MeshActions(
    val startMesh: () -> Unit = {},
    val enableBluetooth: () -> Unit = {},
    val makeDiscoverable: () -> Unit = {},
    val discoverPeers: () -> Unit = {},
    val connectPeer: (PeerDevice) -> Unit = {},
    val sendMessage: (String, String) -> Boolean = { _, _ -> false },
    val pickFile: (String) -> Unit = {},
    val openFile: (String) -> Unit = {},
    val checkUpdate: () -> Unit = {},
    val openLegacyConsole: () -> Unit = {}
)

@Composable
fun MeshApp(
    viewModel: MeshHomeViewModel = viewModel(),
    actions: MeshActions = MeshActions()
) {
    val state by viewModel.uiState.collectAsState()
    MeshTheme {
        val openId = state.openConversationId
        if (openId != null) {
            val conversation = state.openConversation ?: return@MeshTheme
            BackHandler { viewModel.closeThread() }
            ChatThreadScreen(
                conversation = conversation,
                messages = state.threadMessages,
                composerText = state.composerText,
                fileProgress = state.fileProgress,
                onComposerChange = viewModel::setComposerText,
                onBack = viewModel::closeThread,
                onSend = {
                    val sent = actions.sendMessage(openId, state.composerText)
                    if (sent) viewModel.clearComposer()
                },
                onAttach = { actions.pickFile(openId) },
                onOpenFile = actions.openFile
            )
        } else {
            ChatsHomeScreen(
                searchQuery = state.searchQuery,
                conversations = state.filteredConversations,
                stories = state.stories,
                onSearchChange = viewModel::setSearchQuery,
                onOpenSetup = viewModel::openSetup,
                onAdd = {
                    viewModel.openSetup()
                    actions.startMesh()
                },
                onStoryClick = { viewModel.openThread(it.id) },
                onConversationClick = { viewModel.openThread(it.id) }
            )
        }
        if (state.sheetVisible) {
            MeshSetupSheet(
                nodeId = state.nodeId,
                newChatNodeId = state.newChatNodeId,
                meshStatus = state.meshStatus,
                connectionsLabel = state.connectionsLabel,
                logs = state.logs,
                classicPeers = state.classicPeers,
                updateStatus = state.updateStatus,
                updateBusy = state.updateBusy,
                onNodeIdChange = viewModel::setNodeId,
                onNewChatChange = viewModel::setNewChatNodeId,
                onDismiss = viewModel::closeSetup,
                onStartMesh = actions.startMesh,
                onOpenChat = viewModel::openThread,
                onEnableBluetooth = actions.enableBluetooth,
                onDiscoverable = actions.makeDiscoverable,
                onDiscover = actions.discoverPeers,
                onConnectPeer = actions.connectPeer,
                onCheckUpdate = actions.checkUpdate,
                onOpenLegacy = actions.openLegacyConsole
            )
        }
    }
}
