package com.devil1716.bluetoothmanet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.ui.screens.ChatThreadScreen
import com.devil1716.bluetoothmanet.ui.screens.ChatsHomeScreen
import com.devil1716.bluetoothmanet.ui.screens.OnboardingScreen
import com.devil1716.bluetoothmanet.ui.screens.ProfileScreen
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
        if (!state.onboardingComplete && !state.onboarded) {
            OnboardingScreen(
                onSkip = viewModel::completeOnboarding,
                onGetStarted = {
                    viewModel.completeOnboarding()
                    actions.startMesh()
                }
            )
            return@MeshTheme
        }
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
                selectedTab = state.homeTab,
                onSearchChange = viewModel::setSearchQuery,
                onTabChange = viewModel::setHomeTab,
                onStartMesh = actions.startMesh,
                onConversationClick = { viewModel.openThread(it.id) },
                profile = {
                    ProfileScreen(
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
            )
        }
    }
}
