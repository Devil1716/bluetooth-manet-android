package com.devil1716.bluetoothmanet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.ui.screens.ChatThreadScreen
import com.devil1716.bluetoothmanet.ui.screens.ChatsHomeScreen
import com.devil1716.bluetoothmanet.ui.screens.SettingsScreen
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
    val openLocationSettings: () -> Unit = {},
    val openNotificationSettings: () -> Unit = {}
)

@Composable
fun MeshApp(
    viewModel: MeshHomeViewModel = viewModel(),
    actions: MeshActions = MeshActions()
) {
    val state by viewModel.uiState.collectAsState()
    MeshTheme {
        val openId = state.openConversationId
        when {
            state.settingsVisible -> {
                BackHandler { viewModel.closeSettings() }
                SettingsScreen(
                    nodeId = state.nodeId,
                    meshStarted = state.meshStarted,
                    meshStatus = state.meshStatus,
                    connectionsLabel = state.connectionsLabel,
                    logs = state.logs,
                    classicPeers = state.classicPeers,
                    updateStatus = state.update.message,
                    updateBusy = state.update.busy,
                    updateLabel = state.update.primaryLabel,
                    updateProgress = state.update.progress,
                    onNodeIdChange = viewModel::setNodeId,
                    onBack = viewModel::closeSettings,
                    onStartMesh = actions.startMesh,
                    onCopyNodeId = actions.copyNodeId,
                    onShareNodeId = actions.shareNodeId,
                    onEnableBluetooth = actions.enableBluetooth,
                    onRequestPermissions = actions.requestPermissions,
                    onOpenAppSettings = actions.openAppSettings,
                    onOpenLocationSettings = actions.openLocationSettings,
                    onOpenNotificationSettings = actions.openNotificationSettings,
                    onDiscoverable = actions.makeDiscoverable,
                    onDiscover = actions.discoverPeers,
                    onConnectPeer = actions.connectPeer,
                    onCheckUpdate = actions.checkUpdate,
                    onOpenLegacy = actions.openLegacyConsole
                )
            }
            openId != null -> {
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
            }
            else -> {
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
                    showChecklist = !state.showWelcome && shouldShowGetStarted(
                        onboardingComplete = state.onboardingComplete,
                        meshStarted = state.meshStarted,
                        permissionsReady = state.permission.allGranted,
                        bluetoothOn = !state.permission.bluetoothOff
                    ),
                    onSearchChange = viewModel::setSearchQuery,
                    onOpenSetup = viewModel::openSettings,
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
                    onNewChat = viewModel::openNewChat,
                    onNearbyClick = { viewModel.openThread(it.id) },
                    onConversationClick = { viewModel.openThread(it.id) }
                )
            }
        }

        if (state.showWelcome && !state.settingsVisible && openId == null) {
            WelcomeDialog(onContinue = viewModel::dismissWelcome)
        }
        if (state.newChatVisible && !state.settingsVisible && openId == null) {
            NewChatDialog(
                nodeId = state.newChatNodeId,
                onNodeIdChange = viewModel::setNewChatNodeId,
                onDismiss = viewModel::closeNewChat,
                onOpen = { viewModel.openThread(state.newChatNodeId) }
            )
        }
    }
}

@Composable
private fun WelcomeDialog(onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text("Welcome to Mesh") },
        text = {
            Text(
                "Chat with phones around you over Bluetooth. No internet, no pairing, and no account. " +
                    "Stay in the app on both phones, then send a message."
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("Continue") }
        }
    )
}

@Composable
private fun NewChatDialog(
    nodeId: String,
    onNodeIdChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New chat") },
        text = {
            Column {
                Text("Enter the other phone’s ID.")
                OutlinedTextField(
                    value = nodeId,
                    onValueChange = onNodeIdChange,
                    singleLine = true,
                    label = { Text("Their ID") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics { contentDescription = "New chat ID" }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onOpen,
                enabled = nodeId.trim().isNotEmpty()
            ) { Text("Open") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
