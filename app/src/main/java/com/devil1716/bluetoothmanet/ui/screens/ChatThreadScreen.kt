package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ChatMessageEntity
import com.devil1716.bluetoothmanet.MeshFileStore
import com.devil1716.bluetoothmanet.ui.ConversationPreview
import com.devil1716.bluetoothmanet.ui.FileTransferPhase
import com.devil1716.bluetoothmanet.ui.FileTransferUi
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.formatInboxTime
import com.devil1716.bluetoothmanet.ui.theme.MeshBlack
import com.devil1716.bluetoothmanet.ui.theme.MeshDanger
import com.devil1716.bluetoothmanet.ui.theme.MeshIncoming
import com.devil1716.bluetoothmanet.ui.theme.MeshMint
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshOnline
import com.devil1716.bluetoothmanet.ui.theme.MeshOutgoing
import com.devil1716.bluetoothmanet.ui.theme.MeshSurface
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@Composable
fun ChatThreadScreen(
    conversation: ConversationPreview,
    messages: List<ChatMessageEntity>,
    composerText: String,
    fileTransfer: FileTransferUi,
    meshStarted: Boolean,
    onComposerChange: (String) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val canSend = meshStarted && composerText.isNotBlank()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MeshWhite)
            }
            MeshAvatar(name = conversation.name, size = 40.dp, online = conversation.online)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.name, color = MeshWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = buildString {
                        append("Node ")
                        append(conversation.id)
                        append(" · ")
                        append(if (conversation.online) "Online" else "Mesh")
                    },
                    color = MeshMint,
                    fontSize = 12.sp
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "No messages yet. Say hello over the mesh.",
                        color = MeshMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message, onOpenFile = onOpenFile)
            }
        }
        if (fileTransfer.visible) {
            FileTransferBanner(transfer = fileTransfer)
        }
        if (!meshStarted) {
            Text(
                text = "Start Mesh on the home screen before sending. Store-and-forward needs the radio running.",
                color = MeshDanger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAttach,
                enabled = meshStarted,
                modifier = Modifier.semantics { contentDescription = "Attach file" }
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = if (meshStarted) MeshWhite else MeshMuted
                )
            }
            OutlinedTextField(
                value = composerText,
                onValueChange = onComposerChange,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Message composer" },
                enabled = meshStarted,
                placeholder = { Text(if (meshStarted) "Signed message" else "Mesh idle") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MeshWhite,
                    unfocusedTextColor = MeshWhite,
                    disabledTextColor = MeshMuted,
                    focusedBorderColor = MeshMint,
                    unfocusedBorderColor = Color(0xFF3A3A3C),
                    cursorColor = MeshWhite,
                    focusedContainerColor = MeshSurface,
                    unfocusedContainerColor = MeshSurface,
                    disabledContainerColor = MeshSurface
                )
            )
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = "Send message" }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (canSend) MeshMint else MeshMuted
                )
            }
        }
    }
}

@Composable
private fun FileTransferBanner(transfer: FileTransferUi) {
    val (title, color, description) = when (transfer.phase) {
        FileTransferPhase.SENDING -> Triple(
            buildString {
                append(transfer.fileName.ifBlank { "File" })
                if (transfer.sizeLabel.isNotBlank()) {
                    append(" · ")
                    append(transfer.sizeLabel)
                }
            },
            MeshMint,
            if (transfer.total > 0) {
                "${transfer.completed}/${transfer.total} chunks"
            } else {
                "Sending over the mesh…"
            }
        )
        FileTransferPhase.SUCCESS -> Triple(
            "${transfer.fileName.ifBlank { "File" }} sent",
            MeshOnline,
            if (transfer.sizeLabel.isNotBlank()) transfer.sizeLabel else "Transfer complete"
        )
        FileTransferPhase.FAILED -> Triple(
            "File failed",
            MeshDanger,
            transfer.error.ifBlank { "Start mesh and try again." }
        )
        FileTransferPhase.NONE -> return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MeshSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = MeshWhite, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        if (transfer.phase == FileTransferPhase.SENDING) {
            if (transfer.total > 0) {
                LinearProgressIndicator(
                    progress = { transfer.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MeshMint,
                    trackColor = Color(0xFF2A2A2A)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MeshMint,
                    trackColor = Color(0xFF2A2A2A)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageEntity, onOpenFile: (String) -> Unit) {
    val outgoing = message.sentByMe
    val path = MeshFileStore.embeddedPath(message.text)
    val isFile = MeshFileStore.isFileMessage(message.text)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (outgoing) 18.dp else 4.dp,
                        bottomEnd = if (outgoing) 4.dp else 18.dp
                    )
                )
                .background(if (outgoing) MeshOutgoing else MeshIncoming)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = MeshFileStore.displayName(message.text),
                color = MeshWhite,
                fontSize = 16.sp
            )
            Text(
                text = buildString {
                    append(formatInboxTime(message.timestamp))
                    append("  ·  ")
                    append(
                        when {
                            isFile && outgoing -> "file · ${message.status.name.lowercase()}"
                            outgoing -> message.status.name.lowercase()
                            isFile -> "file · verified"
                            else -> "verified"
                        }
                    )
                },
                color = MeshMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (path != null) {
                TextButton(
                    onClick = { onOpenFile(path) },
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("Open file", color = MeshWhite, fontSize = 13.sp)
                }
            }
        }
    }
}
