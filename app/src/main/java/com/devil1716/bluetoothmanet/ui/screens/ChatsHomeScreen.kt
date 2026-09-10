package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ui.ConversationPreview
import com.devil1716.bluetoothmanet.ui.PermissionUi
import com.devil1716.bluetoothmanet.ui.StoryPeer
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.formatInboxTime
import com.devil1716.bluetoothmanet.ui.theme.MeshBlack
import com.devil1716.bluetoothmanet.ui.theme.MeshDanger
import com.devil1716.bluetoothmanet.ui.theme.MeshMint
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshSurface
import com.devil1716.bluetoothmanet.ui.theme.MeshTheme
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite
import com.devil1716.bluetoothmanet.update.UpdatePhase
import com.devil1716.bluetoothmanet.update.UpdateUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsHomeScreen(
    searchQuery: String,
    conversations: List<ConversationPreview>,
    nearbyPeers: List<StoryPeer>,
    nodeId: String,
    statusChip: String,
    livePeerIds: List<String>,
    meshStarted: Boolean,
    permission: PermissionUi,
    nodeIdCopied: Boolean,
    showChecklist: Boolean,
    update: UpdateUi = UpdateUi(),
    onSearchChange: (String) -> Unit,
    onOpenSetup: () -> Unit,
    onCopyNodeId: () -> Unit,
    onShareNodeId: () -> Unit,
    onStartMesh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onUpdateAction: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onNearbyClick: (StoryPeer) -> Unit,
    onConversationClick: (ConversationPreview) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MeshBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chats", fontWeight = FontWeight.Bold)
                        Text(
                            text = statusChip,
                            color = if (livePeerIds.isNotEmpty()) MeshMint else MeshMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSetup,
                        modifier = Modifier.semantics { contentDescription = "Settings" }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MeshWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeshBlack,
                    titleContentColor = MeshWhite,
                    actionIconContentColor = MeshWhite
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                containerColor = MeshMint,
                contentColor = MeshBlack,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New chat", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Search chats" },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MeshMuted)
                    },
                    placeholder = { Text("Search", color = MeshMuted) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MeshWhite,
                        unfocusedTextColor = MeshWhite,
                        focusedBorderColor = MeshMint,
                        unfocusedBorderColor = Color(0xFF3A3A3C),
                        cursorColor = MeshWhite,
                        focusedContainerColor = MeshSurface,
                        unfocusedContainerColor = MeshSurface
                    )
                )
            }
            item {
                NearbyStrip(
                    nearbyPeers = nearbyPeers,
                    onNearbyClick = onNearbyClick
                )
            }
            if (update.bannerVisible) {
                item {
                    UpdateBanner(update = update, onAction = onUpdateAction, onDismiss = onDismissUpdate)
                }
            }
            if (!showChecklist && (permission.showRationale || !permission.allGranted || permission.permanentlyDenied)) {
                item {
                    PermissionBanner(
                        permission = permission,
                        onRequestPermissions = onRequestPermissions,
                        onOpenAppSettings = onOpenAppSettings
                    )
                }
            }
            if (permission.locationServicesOff) {
                item { LocationBanner(onOpenLocationSettings = onOpenLocationSettings) }
            }
            if (permission.bluetoothOff) {
                item { BluetoothBanner(onEnableBluetooth = onEnableBluetooth) }
            }
            if (showChecklist) {
                item {
                    GetStartedCard(
                        nodeId = nodeId,
                        meshStarted = meshStarted,
                        permission = permission,
                        nodeIdCopied = nodeIdCopied,
                        onCopyNodeId = onCopyNodeId,
                        onShareNodeId = onShareNodeId,
                        onStartMesh = onStartMesh,
                        onRequestPermissions = onRequestPermissions,
                        onEnableBluetooth = onEnableBluetooth
                    )
                }
            }
            if (conversations.isEmpty()) {
                item {
                    EmptyConversations(
                        meshStarted = meshStarted,
                        onNewChat = onNewChat
                    )
                }
            } else {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyStrip(
    nearbyPeers: List<StoryPeer>,
    onNearbyClick: (StoryPeer) -> Unit
) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            text = "Online",
            color = MeshMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
        )
        if (nearbyPeers.none { it.online }) {
            Text(
                text = "Nobody nearby yet",
                color = MeshMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(nearbyPeers.filter { it.online }, key = { it.id }) { peer ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { onNearbyClick(peer) }
                            .semantics { contentDescription = "Open chat with ${peer.id}" }
                    ) {
                        MeshAvatar(name = peer.id, size = 52.dp, online = true)
                        Text(
                            text = peer.id,
                            color = MeshWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GetStartedCard(
    nodeId: String,
    meshStarted: Boolean,
    permission: PermissionUi,
    nodeIdCopied: Boolean,
    onCopyNodeId: () -> Unit,
    onShareNodeId: () -> Unit,
    onStartMesh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit
) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Turn on nearby chat", color = MeshWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            text = "Your ID is $nodeId. Share it with the other phone, then both of you stay in the app.",
            color = MeshMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = onCopyNodeId) {
                Text(if (nodeIdCopied) "Copied" else "Copy ID", color = MeshMint)
            }
            TextButton(onClick = onShareNodeId) { Text("Share", color = MeshMint) }
        }
        val action = when {
            !permission.allGranted && !permission.permanentlyDenied ->
                "Allow Bluetooth" to onRequestPermissions
            permission.bluetoothOff -> "Turn on Bluetooth" to onEnableBluetooth
            !meshStarted -> "Start nearby chat" to onStartMesh
            else -> null
        }
        if (action != null) {
            Button(
                onClick = action.second,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MeshMint, contentColor = MeshBlack)
            ) {
                Text(action.first)
            }
        }
    }
}

@Composable
private fun EmptyConversations(meshStarted: Boolean, onNewChat: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MeshAvatar(name = "Mesh", size = 72.dp, online = meshStarted)
        Text(
            text = "No chats yet",
            color = MeshWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = if (meshStarted) {
                "When someone is online, tap their ID. Or start a chat if you already have it."
            } else {
                "Turn on nearby chat, then message a phone around you."
            },
            color = MeshMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        TextButton(onClick = onNewChat, modifier = Modifier.padding(top = 8.dp)) {
            Text("Start a chat", color = MeshMint, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UpdateBanner(
    update: UpdateUi,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = when (update.phase) {
        UpdatePhase.FAILED, UpdatePhase.SIGNATURE_CONFLICT -> MeshDanger
        else -> MeshMint
    }
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (update.phase) {
                        UpdatePhase.AVAILABLE -> "Update available"
                        UpdatePhase.DOWNLOADING -> "Downloading update"
                        UpdatePhase.READY -> "Update ready to install"
                        UpdatePhase.INSTALLING -> "Installing update"
                        UpdatePhase.NEEDS_PERMISSION -> "Allow Mesh to install updates"
                        UpdatePhase.SIGNATURE_CONFLICT -> "This install can't be replaced"
                        UpdatePhase.FAILED -> "Update didn't finish"
                        else -> "Mesh update"
                    },
                    color = MeshWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = update.message.ifBlank { "Tap to update without leaving your chats." },
                    color = MeshMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (update.phase != UpdatePhase.DOWNLOADING && update.phase != UpdatePhase.INSTALLING) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).semantics { contentDescription = "Dismiss update" }
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = MeshMuted)
                }
            }
        }
        if (update.phase == UpdatePhase.DOWNLOADING || update.phase == UpdatePhase.INSTALLING) {
            val indicatorMod = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(4.dp))
            if (update.progress > 0f) {
                LinearProgressIndicator(
                    progress = { update.progress },
                    modifier = indicatorMod,
                    color = accent,
                    trackColor = Color(0xFF2A2A2A)
                )
            } else {
                LinearProgressIndicator(modifier = indicatorMod, color = accent, trackColor = Color(0xFF2A2A2A))
            }
        }
        Button(
            onClick = onAction,
            enabled = update.phase != UpdatePhase.DOWNLOADING && update.phase != UpdatePhase.INSTALLING,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MeshBlack)
        ) {
            Text(update.primaryLabel)
        }
    }
}

@Composable
private fun PermissionBanner(
    permission: PermissionUi,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val denied = permission.permanentlyDenied
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = if (denied) "Bluetooth access is blocked" else "Allow nearby Bluetooth",
            color = MeshWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Text(
            text = if (denied) {
                "Open Android settings and allow Bluetooth (and Location if shown), then come back."
            } else {
                "Mesh uses Bluetooth to find phones around you. No pairing or account. Location is only so Android can look for nearby devices."
            },
            color = MeshMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = if (denied) onOpenAppSettings else onRequestPermissions,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MeshMint, contentColor = MeshBlack)
        ) {
            Text(if (denied) "Open app settings" else "Continue")
        }
    }
}

@Composable
private fun LocationBanner(onOpenLocationSettings: () -> Unit) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MeshDanger, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Location is off", color = MeshWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Text(
            text = "Many phones won't find nearby chats while Location is off.",
            color = MeshMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = onOpenLocationSettings,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = MeshWhite)
        ) {
            Text("Open Location settings")
        }
    }
}

@Composable
private fun BluetoothBanner(onEnableBluetooth: () -> Unit) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Bluetooth is off", color = MeshWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            text = "Turn Bluetooth on so nearby phones can chat.",
            color = MeshMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = onEnableBluetooth,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MeshMint, contentColor = MeshBlack)
        ) {
            Text("Turn on Bluetooth")
        }
    }
}

@Composable
private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MeshSurface)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationPreview,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Open chat with ${conversation.id}" }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeshAvatar(name = conversation.name, size = 54.dp, online = conversation.online)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.name,
                color = MeshWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!conversation.name.equals(conversation.id, ignoreCase = true)) {
                Text(
                    text = conversation.id,
                    color = MeshMint.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (conversation.hasAttachment) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(12.dp)
                            .background(MeshMuted, RoundedCornerShape(2.dp))
                    )
                }
                Text(
                    text = conversation.preview,
                    color = MeshMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val timeLabel = formatInboxTime(conversation.timestamp)
        if (timeLabel.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = timeLabel,
                color = MeshMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Top)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun ChatsHomePreview() {
    MeshTheme {
        ChatsHomeScreen(
            searchQuery = "",
            conversations = emptyList(),
            nearbyPeers = emptyList(),
            nodeId = "K7M2",
            statusChip = "Connecting",
            livePeerIds = emptyList(),
            meshStarted = true,
            permission = PermissionUi(allGranted = true, showRationale = false),
            nodeIdCopied = false,
            showChecklist = false,
            onSearchChange = {},
            onOpenSetup = {},
            onCopyNodeId = {},
            onShareNodeId = {},
            onStartMesh = {},
            onRequestPermissions = {},
            onOpenAppSettings = {},
            onOpenLocationSettings = {},
            onEnableBluetooth = {},
            onNearbyClick = {},
            onConversationClick = {}
        )
    }
}
