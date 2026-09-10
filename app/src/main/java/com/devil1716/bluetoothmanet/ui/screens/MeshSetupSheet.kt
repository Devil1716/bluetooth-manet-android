package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.BuildConfig
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.theme.MeshBlack
import com.devil1716.bluetoothmanet.ui.theme.MeshMint
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshSurface
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    nodeId: String,
    meshStarted: Boolean,
    meshStatus: String,
    connectionsLabel: String,
    logs: String,
    classicPeers: List<PeerDevice>,
    updateStatus: String,
    updateBusy: Boolean,
    updateLabel: String,
    updateProgress: Float,
    onNodeIdChange: (String) -> Unit,
    onBack: () -> Unit,
    onStartMesh: () -> Unit,
    onCopyNodeId: () -> Unit,
    onShareNodeId: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDiscoverable: () -> Unit,
    onDiscover: () -> Unit,
    onConnectPeer: (PeerDevice) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenLegacy: () -> Unit
) {
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MeshBlack,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MeshWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeshBlack,
                    titleContentColor = MeshWhite,
                    navigationIconContentColor = MeshWhite
                )
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeshAvatar(name = nodeId, size = 56.dp, online = meshStarted)
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("My ID", color = MeshMuted, fontSize = 12.sp)
                    Text(nodeId, color = MeshWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(meshStatus, color = MeshMint, fontSize = 13.sp)
                }
            }
            OutlinedTextField(
                value = nodeId,
                onValueChange = onNodeIdChange,
                label = { Text("Edit ID") },
                supportingText = { Text("Each phone needs its own ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Your ID" },
                colors = meshFieldColors()
            )
            Row {
                TextButton(onClick = onCopyNodeId) { Text("Copy ID", color = MeshMint) }
                TextButton(onClick = onShareNodeId) { Text("Share", color = MeshMint) }
            }

            SettingsSection("Bluetooth & permissions") {
                Text(connectionsLabel, color = MeshMuted, fontSize = 13.sp)
                Button(
                    onClick = onStartMesh,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeshMint, contentColor = MeshBlack)
                ) {
                    Text(if (meshStarted) "Nearby chat is on" else "Start nearby chat")
                }
                TextButton(onClick = onRequestPermissions) { Text("Bluetooth permission", color = MeshMint) }
                TextButton(onClick = onEnableBluetooth) { Text("Turn on Bluetooth", color = MeshMint) }
                TextButton(onClick = onOpenLocationSettings) { Text("Location settings", color = MeshMint) }
                TextButton(onClick = onOpenAppSettings) { Text("App settings", color = MeshMint) }
            }

            SettingsSection("Notifications") {
                Text("Mesh can show a quiet notice while nearby chat is on.", color = MeshMuted, fontSize = 13.sp)
                TextButton(onClick = onOpenNotificationSettings) { Text("Notification settings", color = MeshMint) }
            }

            SettingsSection("Updates") {
                Button(
                    onClick = onCheckUpdate,
                    enabled = !updateBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = MeshWhite)
                ) {
                    Text(updateLabel)
                }
                if (updateBusy && updateProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { updateProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MeshMint,
                        trackColor = Color(0xFF2A2A2A)
                    )
                }
                if (updateStatus.isNotBlank()) {
                    Text(updateStatus, color = MeshMint, fontSize = 13.sp)
                }
            }

            SettingsSection("About") {
                Text(
                    text = "Mesh ${BuildConfig.VERSION_NAME}",
                    color = MeshWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onOpenLegacy)
                )
                Text(
                    "Chat with phones around you. No internet, no pairing, no account.",
                    color = MeshMuted,
                    fontSize = 13.sp
                )
            }

            TextButton(
                onClick = { advancedOpen = !advancedOpen },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Advanced" }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("Advanced", color = MeshWhite, fontWeight = FontWeight.SemiBold)
                        Text("Laptop / paired devices and diagnostics", color = MeshMuted, fontSize = 12.sp)
                    }
                    Icon(
                        if (advancedOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (advancedOpen) "Collapse" else "Expand",
                        tint = MeshMuted
                    )
                }
            }
            if (advancedOpen) {
                Text(
                    "Only needed for a Windows laptop or an already-paired Bluetooth device. Phone-to-phone chat does not use this.",
                    color = MeshMuted,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDiscoverable, modifier = Modifier.weight(1f)) {
                        Text("Make visible", color = MeshMint)
                    }
                    TextButton(onClick = onDiscover, modifier = Modifier.weight(1f)) {
                        Text("Find devices", color = MeshMint)
                    }
                }
                classicPeers.forEach { peer ->
                    TextButton(onClick = { onConnectPeer(peer) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${peer.name}\n${peer.address}", color = MeshWhite)
                    }
                }
                TextButton(onClick = { diagnosticsOpen = !diagnosticsOpen }) {
                    Text(if (diagnosticsOpen) "Hide diagnostics" else "Diagnostics", color = MeshMuted)
                }
                if (diagnosticsOpen) {
                    Text(
                        text = logs.takeLast(1200).ifBlank { "No events yet." },
                        color = MeshMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(MeshSurface, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = MeshWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun meshFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MeshWhite,
    unfocusedTextColor = MeshWhite,
    focusedLabelColor = MeshMint,
    unfocusedLabelColor = MeshMuted,
    focusedBorderColor = MeshMint,
    unfocusedBorderColor = MeshMuted,
    cursorColor = MeshWhite,
    focusedSupportingTextColor = MeshMuted,
    unfocusedSupportingTextColor = MeshMuted
)
