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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ChatMessageEntity
import com.devil1716.bluetoothmanet.MeshFileStore
import com.devil1716.bluetoothmanet.MessageStatus
import com.devil1716.bluetoothmanet.ui.ConversationPreview
import com.devil1716.bluetoothmanet.ui.components.MeshAtmosphere
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.formatChatTime
import com.devil1716.bluetoothmanet.ui.theme.AccentGradient
import com.devil1716.bluetoothmanet.ui.theme.BubbleGradient
import com.devil1716.bluetoothmanet.ui.theme.MeshAccent
import com.devil1716.bluetoothmanet.ui.theme.MeshComposer
import com.devil1716.bluetoothmanet.ui.theme.MeshIncoming
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite
import com.devil1716.bluetoothmanet.ui.messageReceiptLabel
import com.devil1716.bluetoothmanet.ui.theme.MeshDanger

@Composable
fun ChatThreadScreen(
    conversation: ConversationPreview,
    messages: List<ChatMessageEntity>,
    composerText: String,
    fileProgress: String,
    identityWarning: String? = null,
    onComposerChange: (String) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val listScope = rememberCoroutineScope()
    var stickToBottom by remember { mutableStateOf(true) }
    var unseenWhileReading by remember { mutableStateOf(false) }
    var lastCount by remember { mutableStateOf(messages.size) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (!scrolling) {
                    stickToBottom = !canScrollForward
                    if (stickToBottom) unseenWhileReading = false
                }
            }
    }
    LaunchedEffect(messages.size) {
        val grew = messages.size > lastCount
        lastCount = messages.size
        if (messages.isEmpty()) return@LaunchedEffect
        if (stickToBottom) {
            listState.scrollToItem(messages.lastIndex)
            unseenWhileReading = false
        } else if (grew) {
            unseenWhileReading = true
        }
    }
    MeshAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MeshWhite)
                }
                MeshAvatar(name = conversation.name, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(conversation.name, color = MeshWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = conversation.distanceLabel.ifBlank { if (conversation.online) "Nearby" else "On the mesh" },
                        color = MeshMuted,
                        fontSize = 12.sp
                    )
                }
            }
            if (!identityWarning.isNullOrBlank()) {
                Text(
                    text = "This phone’s signing key changed. Known fingerprint $identityWarning. New messages from the replacement key are ignored until you confirm the new identity in person.",
                    color = MeshDanger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics {
                            contentDescription = "Identity warning. Fingerprint $identityWarning"
                        }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "No messages yet. Say hello to someone nearby.",
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
            if (unseenWhileReading) {
                TextButton(
                    onClick = {
                        stickToBottom = true
                        unseenWhileReading = false
                        listScope.launch {
                            if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MeshWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("New messages", color = MeshWhite, fontSize = 13.sp)
                }
            }
            }
            if (fileProgress.isNotBlank() && fileProgress != "No file transfer") {
                Text(
                    text = fileProgress,
                    color = MeshMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.dp, MeshMuted.copy(alpha = 0.35f), CircleShape)
                        .clickable(onClick = onAttach),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Send file", tint = MeshWhite, modifier = Modifier.size(20.dp))
                }
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MeshComposer)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = composerText,
                        onValueChange = onComposerChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 24.dp, max = 96.dp)
                            .semantics { contentDescription = "Message" },
                        textStyle = TextStyle(color = MeshWhite, fontSize = 16.sp),
                        cursorBrush = SolidColor(MeshWhite),
                        maxLines = 5,
                        decorationBox = { inner ->
                            if (composerText.isEmpty()) {
                                Text("Message...", color = MeshMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .then(
                            if (composerText.isNotBlank()) Modifier.background(AccentGradient)
                            else Modifier.background(MeshComposer)
                        )
                        .clickable(enabled = composerText.isNotBlank(), onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (composerText.isNotBlank()) MeshWhite else MeshMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageEntity, onOpenFile: (String) -> Unit) {
    val outgoing = message.sentByMe
    val path = MeshFileStore.embeddedPath(message.text)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (outgoing) 18.dp else 6.dp,
                        bottomEnd = if (outgoing) 6.dp else 18.dp
                    )
                )
                .then(
                    if (outgoing) Modifier.background(BubbleGradient)
                    else Modifier.background(MeshIncoming)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = MeshFileStore.displayName(message.text),
                color = MeshWhite,
                fontSize = 16.sp,
                lineHeight = 22.sp
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 4.dp, start = 6.dp, end = 6.dp)
                .semantics {
                    contentDescription = if (outgoing) {
                        val receipt = messageReceiptLabel(true, message.status.name)
                        listOf(formatChatTime(message.timestamp), receipt)
                            .filter { it.isNotBlank() }
                            .joinToString(". ")
                    } else {
                        formatChatTime(message.timestamp)
                    }
                }
        ) {
            val receipt = messageReceiptLabel(outgoing, message.status.name)
            Text(
                text = if (receipt.isBlank()) formatChatTime(message.timestamp)
                else "${formatChatTime(message.timestamp)} · $receipt",
                color = if (message.status == MessageStatus.FAILED) MeshDanger else MeshMuted,
                fontSize = 11.sp
            )
            if (outgoing && message.status == MessageStatus.DELIVERED) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Delivered",
                    tint = MeshAccent,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(12.dp)
                )
            }
        }
    }
}
