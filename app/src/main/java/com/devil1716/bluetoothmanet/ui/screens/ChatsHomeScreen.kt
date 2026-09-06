package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ui.ConversationPreview
import com.devil1716.bluetoothmanet.ui.StoryPeer
import com.devil1716.bluetoothmanet.ui.components.AddStoryAvatar
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.firstName
import com.devil1716.bluetoothmanet.ui.formatInboxTime
import com.devil1716.bluetoothmanet.ui.theme.HeaderGradient
import com.devil1716.bluetoothmanet.ui.theme.MeshBlack
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshSearchFill
import com.devil1716.bluetoothmanet.ui.theme.MeshTheme
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@Composable
fun ChatsHomeScreen(
    searchQuery: String,
    conversations: List<ConversationPreview>,
    stories: List<StoryPeer>,
    onSearchChange: (String) -> Unit,
    onOpenSetup: () -> Unit,
    onAdd: () -> Unit,
    onStoryClick: (StoryPeer) -> Unit,
    onConversationClick: (ConversationPreview) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshBlack)
            .navigationBarsPadding()
    ) {
        GradientHeader(
            searchQuery = searchQuery,
            stories = stories,
            onSearchChange = onSearchChange,
            onOpenSetup = onOpenSetup,
            onAdd = onAdd,
            onStoryClick = onStoryClick
        )
        if (conversations.isEmpty()) {
            Text(
                text = "No conversations yet. Add a nearby node to start chatting.",
                color = MeshMuted,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
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
private fun GradientHeader(
    searchQuery: String,
    stories: List<StoryPeer>,
    onSearchChange: (String) -> Unit,
    onOpenSetup: () -> Unit,
    onAdd: () -> Unit,
    onStoryClick: (StoryPeer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(HeaderGradient)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MeshSearchFill)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MeshWhite,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    cursorBrush = SolidColor(MeshWhite),
                    textStyle = TextStyle(color = MeshWhite, fontSize = 15.sp),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search...", color = MeshWhite.copy(alpha = 0.8f), fontSize = 15.sp)
                        }
                        inner()
                    }
                )
            }
            IconButton(onClick = onOpenSetup) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Mesh setup",
                    tint = MeshWhite
                )
            }
        }
        Text(
            text = "Let's Stay\nConnected",
            color = MeshWhite,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 38.sp,
            modifier = Modifier.padding(top = 22.dp, end = 12.dp)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable(onClick = onAdd)
                ) {
                    AddStoryAvatar(size = 58.dp)
                    Text(
                        text = "Add",
                        color = MeshWhite,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            items(stories, key = { it.id }) { peer ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { onStoryClick(peer) }
                ) {
                    MeshAvatar(name = peer.name, size = 58.dp, online = peer.online)
                    Text(
                        text = firstName(peer.name),
                        color = MeshWhite,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
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
                conversations = listOf(
                    ConversationPreview("Adison", "Adison Lubin", "Do you want to grab coffee this weekend?", System.currentTimeMillis(), true, false),
                    ConversationPreview("James", "James Vetrovs", "Let me know if you need anything", System.currentTimeMillis() - 3_600_000, false, true),
                    ConversationPreview("Ashlynn", "Ashlynn Mango", "I'll be a little late, hope that's okay", System.currentTimeMillis() - 86_400_000, false, false),
                    ConversationPreview("Tatiana", "Tatiana Vaccaro", "Just saw this and thought of you!", System.currentTimeMillis() - 90_000_000, true, true),
                    ConversationPreview("Nolan", "Nolan Siphron", "Hey, are you free later?", System.currentTimeMillis() - 5L * 86_400_000, false, false)
                ),
                stories = listOf(
                    StoryPeer("Adison", "Adison", true),
                    StoryPeer("Charlie", "Charlie", false),
                    StoryPeer("James", "James", false)
                ),
                onSearchChange = {},
                onOpenSetup = {},
                onAdd = {},
                onStoryClick = {},
                onConversationClick = {}
            )
        }
    }
}
