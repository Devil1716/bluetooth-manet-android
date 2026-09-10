package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.ui.theme.MeshBlack
import com.devil1716.bluetoothmanet.ui.theme.MeshMint
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshSetupSheet(
    nodeId: String,
    newChatNodeId: String,
    meshStatus: String,
    connectionsLabel: String,
    logs: String,
    classicPeers: List<PeerDevice>,
    updateStatus: String,
    updateBusy: Boolean,
    onNodeIdChange: (String) -> Unit,
    onNewChatChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onStartMesh: () -> Unit,
    onOpenChat: (String) -> Unit,
    onEnableBluetooth: () -> Unit,
    onDiscoverable: () -> Unit,
    onDiscover: () -> Unit,
    onConnectPeer: (PeerDevice) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenLegacy: () -> Unit
) {
    var classicExpanded by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111111),
        contentColor = MeshWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Mesh setup", color = MeshWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Nearby phones link automatically over BLE. No pairing for phone ↔ phone.",
                color = MeshMint,
                fontSize = 14.sp
            )
            Text(meshStatus, color = MeshWhite, fontSize = 13.sp)
            Text(connectionsLabel, color = MeshMuted, fontSize = 13.sp)

            OutlinedTextField(
                value = nodeId,
                onValueChange = onNodeIdChange,
                label = { Text("Your node ID") },
                supportingText = { Text("Must be unique on each phone") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Your node ID" },
                colors = meshFieldColors()
            )
            Button(
                onClick = onStartMesh,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MeshMint, contentColor = MeshBlack)
            ) {
                Text("Start Mesh")
            }

            Text("New chat", color = MeshWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = newChatNodeId,
                onValueChange = onNewChatChange,
                label = { Text("To (node ID)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Destination node ID" },
                colors = meshFieldColors()
            )
            Button(
                onClick = { onOpenChat(newChatNodeId) },
                enabled = newChatNodeId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = MeshWhite)
            ) {
                Text("Open chat")
            }

            TextButton(
                onClick = { classicExpanded = !classicExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Windows and paired devices" }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("Windows / paired devices", color = MeshWhite, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Classic RFCOMM only. Not required for phone ↔ phone BLE.",
                            color = MeshMuted,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = if (classicExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (classicExpanded) "Collapse" else "Expand",
                        tint = MeshMuted
                    )
                }
            }
            if (classicExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEnableBluetooth, modifier = Modifier.weight(1f)) {
                        Text("Enable Bluetooth", color = MeshMint)
                    }
                    TextButton(onClick = onDiscoverable, modifier = Modifier.weight(1f)) {
                        Text("Discoverable", color = MeshMint)
                    }
                }
                Button(
                    onClick = onDiscover,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = MeshWhite)
                ) {
                    Text("Find classic peers")
                }
                classicPeers.forEach { peer ->
                    TextButton(onClick = { onConnectPeer(peer) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${peer.name}\n${peer.address}", color = MeshWhite)
                    }
                }
                TextButton(onClick = onOpenLegacy, modifier = Modifier.fillMaxWidth()) {
                    Text("Classic mesh console", color = MeshMuted)
                }
            }

            Button(
                onClick = onCheckUpdate,
                enabled = !updateBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = MeshWhite)
            ) {
                Text(if (updateBusy) "Updating…" else "Check update")
            }
            if (updateStatus.isNotBlank()) {
                Text(updateStatus, color = MeshMint, fontSize = 13.sp)
            }
            Text(
                text = logs.takeLast(1200),
                color = MeshMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFF050505), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
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
