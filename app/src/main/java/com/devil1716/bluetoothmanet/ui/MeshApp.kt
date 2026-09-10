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
import com.devil1716.bluetoothmanet.update.UpdatePhase

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
    val applyUpdate: () -> Unit = {},
    val dismissUpdate: () -> Unit = {},
    val uninstallForUpdate: () -> Unit = {},
    val openUpdatePage: () -> Unit = {},
    val openLegacyConsole: () -> Unit = {},
    val copyNodeId: () -> Unit = {},
    val shareNodeId: () -> Unit = {},
    val requestPermissions: () -> Unit = {},
    val openAppSettings: () -> Unit = {},
    val openLocationSettings: () -> Unit = {}
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
                fileTransfer = state.fileTransfer,
                meshStarted = state.meshStarted,
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
                nearbyPeers = state.stories,
                nodeId = state.nodeId,
                statusChip = state.statusChip,
                livePeerIds = state.livePeerIds,
                meshStarted = state.meshStarted,
                permission = state.permission,
                nodeIdCopied = state.nodeIdCopied,
                update = state.update,
                showChecklist = shouldShowFirstRunChecklist(
                    conversationCount = state.conversations.size,
                    meshStarted = state.meshStarted,
                    permissionsReady = state.permission.allGranted,
                    bluetoothOn = !state.permission.bluetoothOff
                ),
                onSearchChange = viewModel::setSearchQuery,
                onOpenSetup = viewModel::openSetup,
                onCopyNodeId = actions.copyNodeId,
                onShareNodeId = actions.shareNodeId,
                onStartMesh = actions.startMesh,
                onRequestPermissions = actions.requestPermissions,
                onOpenAppSettings = actions.openAppSettings,
                onOpenLocationSettings = actions.openLocationSettings,
                onEnableBluetooth = actions.enableBluetooth,
                onUpdateAction = {
                    if (state.update.phase == UpdatePhase.SIGNATURE_CONFLICT) {
                        actions.uninstallForUpdate()
                    } else {
                        actions.applyUpdate()
                    }
                },
                onDismissUpdate = actions.dismissUpdate,
                onNearbyClick = { viewModel.openThread(it.id) },
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
                updateStatus = state.update.message,
                updateBusy = state.update.busy,
                updateLabel = state.update.primaryLabel,
                updateProgress = state.update.progress,
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
