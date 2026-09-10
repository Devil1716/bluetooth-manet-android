package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.R
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.components.MeshPrimaryButton
import com.devil1716.bluetoothmanet.ui.theme.MeshAccent
import com.devil1716.bluetoothmanet.ui.theme.MeshCard
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@Composable
fun ProfileScreen(
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
    onStartMesh: () -> Unit,
    onOpenChat: (String) -> Unit,
    onEnableBluetooth: () -> Unit,
    onDiscoverable: () -> Unit,
    onDiscover: () -> Unit,
    onConnectPeer: (PeerDevice) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenLegacy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_mesh_logo),
            contentDescription = "MESH",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(Modifier.height(20.dp))
        MeshAvatar(name = nodeId, size = 64.dp)
        Text(
            text = nodeId.ifBlank { "You" },
            color = MeshWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(meshStatus, color = MeshAccent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(24.dp))
        MeshPrimaryButton(
            text = "Start Mesh",
            modifier = Modifier.fillMaxWidth(),
            onClick = onStartMesh
        )
        Spacer(Modifier.height(20.dp))
        ProfileCard {
            OutlinedTextField(
                value = nodeId,
                onValueChange = onNodeIdChange,
                label = { Text("Your name / node ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = meshFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Text(connectionsLabel, color = MeshMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        ProfileCard {
            Text("New chat", color = MeshWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newChatNodeId,
                onValueChange = onNewChatChange,
                label = { Text("To (node ID)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = meshFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onOpenChat(newChatNodeId) },
                enabled = newChatNodeId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22283C), contentColor = MeshWhite)
            ) {
                Text("Open chat")
            }
        }
        Spacer(Modifier.height(16.dp))
        ProfileCard {
            Text("Bluetooth", color = MeshWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onEnableBluetooth, modifier = Modifier.weight(1f)) {
                    Text("Enable", color = MeshAccent)
                }
                TextButton(onClick = onDiscoverable, modifier = Modifier.weight(1f)) {
                    Text("Discoverable", color = MeshAccent)
                }
            }
            Button(
                onClick = onDiscover,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22283C), contentColor = MeshWhite)
            ) {
                Text("Find classic peers")
            }
            classicPeers.forEach { peer ->
                TextButton(onClick = { onConnectPeer(peer) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${peer.name}\n${peer.address}", color = MeshWhite)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onCheckUpdate,
            enabled = !updateBusy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22283C), contentColor = MeshWhite)
        ) {
            Text(if (updateBusy) "Updating…" else "Check update")
        }
        if (updateStatus.isNotBlank()) {
            Text(updateStatus, color = MeshAccent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        TextButton(onClick = onOpenLegacy, modifier = Modifier.fillMaxWidth()) {
            Text("Classic mesh console", color = MeshMuted)
        }
        Text(
            text = logs.takeLast(1200),
            color = MeshMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF050814))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                .padding(10.dp)
        )
    }
}

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MeshCard)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun meshFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MeshWhite,
    unfocusedTextColor = MeshWhite,
    focusedLabelColor = MeshAccent,
    unfocusedLabelColor = MeshMuted,
    focusedBorderColor = MeshAccent,
    unfocusedBorderColor = MeshMuted,
    cursorColor = MeshWhite
)
