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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.devil1716.bluetoothmanet.update.UpdatePhase
import com.devil1716.bluetoothmanet.update.UpdateUi
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.formatInboxTime
import com.devil1716.bluetoothmanet.ui.theme.HeaderGradient
import com.devil1716.bluetoothmanet.ui.theme.MeshBlack
import com.devil1716.bluetoothmanet.ui.theme.MeshChipIdle
import com.devil1716.bluetoothmanet.ui.theme.MeshChipLive
import com.devil1716.bluetoothmanet.ui.theme.MeshDanger
import com.devil1716.bluetoothmanet.ui.theme.MeshMint
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshOnline
import com.devil1716.bluetoothmanet.ui.theme.MeshSurface
import com.devil1716.bluetoothmanet.ui.theme.MeshTheme
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

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
    onNearbyClick: (StoryPeer) -> Unit,
    onConversationClick: (ConversationPreview) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshBlack)
            .navigationBarsPadding()
    ) {
        HomeHeader(
            searchQuery = searchQuery,
            nearbyPeers = nearbyPeers,
            nodeId = nodeId,
            statusChip = statusChip,
            livePeerIds = livePeerIds,
            meshStarted = meshStarted,
            nodeIdCopied = nodeIdCopied,
            onSearchChange = onSearchChange,
            onOpenSetup = onOpenSetup,
            onCopyNodeId = onCopyNodeId,
            onShareNodeId = onShareNodeId,
            onNearbyClick = onNearbyClick
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            if (update.bannerVisible) {
                item {
                    UpdateBanner(
                        update = update,
                        onAction = onUpdateAction,
                        onDismiss = onDismissUpdate
                    )
                }
            }
            if (permission.showRationale || !permission.allGranted || permission.permanentlyDenied) {
                item {
                    PermissionBanner(
                        permission = permission,
                        onRequestPermissions = onRequestPermissions,
                        onOpenAppSettings = onOpenAppSettings
                    )
                }
            }
            if (permission.locationServicesOff) {
                item {
                    LocationBanner(onOpenLocationSettings = onOpenLocationSettings)
                }
            }
            if (permission.bluetoothOff) {
                item {
                    BluetoothBanner(onEnableBluetooth = onEnableBluetooth)
                }
            }
            if (showChecklist) {
                item {
                    FirstRunChecklist(
                        nodeId = nodeId,
                        meshStarted = meshStarted,
                        permission = permission,
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
                    EmptyConversationsHint(meshStarted = meshStarted, nodeId = nodeId)
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
private fun HomeHeader(
    searchQuery: String,
    nearbyPeers: List<StoryPeer>,
    nodeId: String,
    statusChip: String,
    livePeerIds: List<String>,
    meshStarted: Boolean,
    nodeIdCopied: Boolean,
    onSearchChange: (String) -> Unit,
    onOpenSetup: () -> Unit,
    onCopyNodeId: () -> Unit,
    onShareNodeId: () -> Unit,
    onNearbyClick: (StoryPeer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(HeaderGradient)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Search chats" },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MeshWhite
                    )
                },
                placeholder = { Text("Search chats", color = MeshWhite.copy(alpha = 0.8f)) },
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MeshWhite,
                    unfocusedTextColor = MeshWhite,
                    focusedBorderColor = MeshWhite.copy(alpha = 0.7f),
                    unfocusedBorderColor = MeshWhite.copy(alpha = 0.35f),
                    cursorColor = MeshWhite,
                    focusedContainerColor = Color(0x33FFFFFF),
                    unfocusedContainerColor = Color(0x22FFFFFF)
                )
            )
            TextButton(
                onClick = onOpenSetup,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics { contentDescription = "Mesh settings" }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = MeshWhite)
                    Text("Setup", color = MeshWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Text(
            text = "Nearby mesh",
            color = MeshWhite,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = "Nearby phones link automatically over BLE — no pairing.",
            color = MeshWhite.copy(alpha = 0.88f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, end = 8.dp)
        )
        Row(
            modifier = Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NodeIdChip(
                nodeId = nodeId,
                copied = nodeIdCopied,
                onClick = onCopyNodeId
            )
            IconButton(
                onClick = onShareNodeId,
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "Share node ID $nodeId" }
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = MeshWhite)
            }
            StatusChip(label = statusChip, live = meshStarted && livePeerIds.isNotEmpty())
        }
        Text(
            text = "Nearby",
            color = MeshWhite.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            item {
                NearbyPeerItem(
                    label = "You",
                    nodeId = nodeId,
                    online = meshStarted,
                    onClick = onCopyNodeId
                )
            }
            items(nearbyPeers, key = { it.id }) { peer ->
                NearbyPeerItem(
                    label = peer.id,
                    nodeId = peer.id,
                    online = peer.online,
                    onClick = { onNearbyClick(peer) }
                )
            }
        }
    }
}

@Composable
private fun NearbyPeerItem(
    label: String,
    nodeId: String,
    online: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (label == "You") "Your node $nodeId" else "Open chat with $nodeId" }
    ) {
        MeshAvatar(name = nodeId, size = 52.dp, online = online)
        Text(
            text = label,
            color = MeshWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (label == "You") {
            Text(
                text = nodeId,
                color = MeshWhite.copy(alpha = 0.85f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = "chat",
                color = MeshWhite.copy(alpha = 0.75f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun NodeIdChip(nodeId: String, copied: Boolean, onClick: () -> Unit) {
    val label = if (copied) "Copied $nodeId" else "You · $nodeId"
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x33000000))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Your node ID $nodeId, tap to copy" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MeshWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusChip(label: String, live: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (live) MeshChipLive else MeshChipIdle)
            .semantics { contentDescription = "Mesh status $label" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (live) MeshOnline else MeshWhite.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = MeshWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
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
        UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING -> MeshMint
        else -> MeshMint
    }
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (update.phase) {
                        UpdatePhase.AVAILABLE -> "Mesh ${update.availableVersion} is available"
                        UpdatePhase.DOWNLOADING -> "Downloading Mesh ${update.availableVersion}"
                        UpdatePhase.READY -> "Mesh ${update.availableVersion} is ready"
                        UpdatePhase.INSTALLING -> "Installing Mesh ${update.availableVersion}"
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
                    text = update.message.ifBlank {
                        "Tap to update without leaving your chats."
                    },
                    color = MeshMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (update.phase != UpdatePhase.DOWNLOADING && update.phase != UpdatePhase.INSTALLING) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Dismiss update" }
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = MeshMuted)
                }
            }
        }
        if (update.phase == UpdatePhase.DOWNLOADING || update.phase == UpdatePhase.INSTALLING) {
            if (update.progress > 0f) {
                LinearProgressIndicator(
                    progress = { update.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = Color(0xFF2A2A2A)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = Color(0xFF2A2A2A)
                )
            }
        }
        Button(
            onClick = onAction,
            enabled = update.phase != UpdatePhase.DOWNLOADING && update.phase != UpdatePhase.INSTALLING,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
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
                "Permissions were denied. Open Android settings, allow Bluetooth (and Location if shown), then return here."
            } else {
                "This app uses Bluetooth Low Energy to find nearby phones. No pairing or accounts. Location is only used so Android can BLE-scan."
            },
            color = MeshMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = if (denied) onOpenAppSettings else onRequestPermissions,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MeshMint, contentColor = MeshBlack)
        ) {
            Text(if (denied) "Open app settings" else "Continue to system prompt")
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
            text = "Many phones will not BLE-scan while Location is off. Turn it on, then Start Mesh.",
            color = MeshMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = onOpenLocationSettings,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
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
            text = "Turn Bluetooth on so nearby phones can link over BLE.",
            color = MeshMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = onEnableBluetooth,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MeshMint, contentColor = MeshBlack)
        ) {
            Text("Enable Bluetooth")
        }
    }
}

@Composable
private fun FirstRunChecklist(
    nodeId: String,
    meshStarted: Boolean,
    permission: PermissionUi,
    onCopyNodeId: () -> Unit,
    onShareNodeId: () -> Unit,
    onStartMesh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit
) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Get to the first chat", color = MeshWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            text = "Share your ID with the other phone. Phone-to-phone does not use classic pairing.",
            color = MeshMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        ChecklistRow(
            done = nodeId.isNotBlank(),
            title = "Your node ID is $nodeId",
            subtitle = "Tap to copy. Each phone needs a unique ID."
        ) {
            TextButton(onClick = onCopyNodeId) { Text("Copy", color = MeshMint) }
            IconButton(
                onClick = onShareNodeId,
                modifier = Modifier.semantics { contentDescription = "Share node ID" }
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = MeshMint)
            }
        }
        ChecklistRow(
            done = permission.allGranted,
            title = "Allow Bluetooth",
            subtitle = if (permission.permanentlyDenied) {
                "Denied — recover from the banner above."
            } else {
                "In-app explanation first, then the system dialog."
            }
        ) {
            if (!permission.allGranted && !permission.permanentlyDenied) {
                TextButton(onClick = onRequestPermissions) { Text("Allow", color = MeshMint) }
            }
        }
        if (permission.bluetoothOff) {
            ChecklistRow(done = false, title = "Turn Bluetooth on", subtitle = "Required before Start Mesh.") {
                TextButton(onClick = onEnableBluetooth) { Text("Enable", color = MeshMint) }
            }
        }
        val startEnabled = permission.readyForMesh
        ChecklistRow(
            done = meshStarted,
            title = if (meshStarted) "Mesh is running" else "Start Mesh",
            subtitle = if (meshStarted) {
                "Waiting for a nearby phone with a different node ID."
            } else {
                "Primary action after permissions. Both phones must start the mesh."
            }
        ) {
            if (!meshStarted) {
                Button(
                    onClick = onStartMesh,
                    enabled = startEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeshMint,
                        contentColor = MeshBlack,
                        disabledContainerColor = Color(0xFF2A2A2A),
                        disabledContentColor = MeshMuted
                    )
                ) {
                    Text("Start Mesh")
                }
            }
        }
        ChecklistRow(
            done = false,
            title = "Share $nodeId with your peer",
            subtitle = "They start a chat with this ID, or appear under Nearby."
        ) {
            TextButton(onClick = onShareNodeId) { Text("Share", color = MeshMint) }
        }
    }
}

@Composable
private fun ChecklistRow(
    done: Boolean,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (done) MeshOnline else Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = "Done", tint = MeshBlack, modifier = Modifier.size(14.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, color = MeshWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MeshMuted, fontSize = 12.sp)
        }
        action()
    }
}

@Composable
private fun EmptyConversationsHint(meshStarted: Boolean, nodeId: String) {
    Text(
        text = if (meshStarted) {
            "No chats yet. When a neighbor appears under Nearby, tap it — or share $nodeId so they can message you."
        } else {
            "No conversations yet. Finish the checklist above to start the mesh."
        },
        color = MeshMuted,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
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
        Box(Modifier.background(MeshBlack)) {
            ChatsHomeScreen(
                searchQuery = "",
                conversations = emptyList(),
                nearbyPeers = emptyList(),
                nodeId = "K7M2",
                statusChip = "Idle",
                livePeerIds = emptyList(),
                meshStarted = false,
                permission = PermissionUi(showRationale = true),
                nodeIdCopied = false,
                showChecklist = true,
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
}
