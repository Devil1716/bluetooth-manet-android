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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ui.ConversationPreview
import com.devil1716.bluetoothmanet.ui.MeshHomeTab
import com.devil1716.bluetoothmanet.ui.NearbyEmptyCopy
import com.devil1716.bluetoothmanet.ui.components.MeshAtmosphere
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.components.MeshPrimaryButton
import com.devil1716.bluetoothmanet.ui.formatInboxTime
import com.devil1716.bluetoothmanet.ui.theme.MeshAccent
import com.devil1716.bluetoothmanet.ui.theme.MeshAway
import com.devil1716.bluetoothmanet.ui.theme.MeshBar
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshOnline
import com.devil1716.bluetoothmanet.ui.theme.MeshSearchFill
import com.devil1716.bluetoothmanet.ui.theme.MeshTheme
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@Composable
fun ChatsHomeScreen(
    searchQuery: String,
    conversations: List<ConversationPreview>,
    selectedTab: MeshHomeTab,
    onSearchChange: (String) -> Unit,
    onTabChange: (MeshHomeTab) -> Unit,
    onStartMesh: () -> Unit,
    onNearbyAction: () -> Unit = onStartMesh,
    nearbyEmpty: NearbyEmptyCopy = NearbyEmptyCopy(
        title = "No one nearby yet",
        body = "Start Mesh to discover people around you over Bluetooth.",
        actionLabel = "Start Mesh"
    ),
    onConversationClick: (ConversationPreview) -> Unit,
    profile: @Composable () -> Unit
) {
    MeshAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            when (selectedTab) {
                MeshHomeTab.Nearby -> NearbyPane(
                    people = conversations,
                    emptyCopy = nearbyEmpty,
                    onOpenSaved = { onTabChange(MeshHomeTab.Chats) },
                    onNearbyAction = onNearbyAction,
                    onPersonClick = onConversationClick,
                    modifier = Modifier.weight(1f)
                )
                MeshHomeTab.Chats -> ChatsPane(
                    searchQuery = searchQuery,
                    conversations = conversations,
                    onSearchChange = onSearchChange,
                    onConversationClick = onConversationClick,
                    modifier = Modifier.weight(1f)
                )
                MeshHomeTab.Profile -> Box(Modifier.weight(1f)) { profile() }
            }
            MeshBottomBar(selectedTab = selectedTab, onTabChange = onTabChange)
        }
    }
}

@Composable
private fun NearbyPane(
    people: List<ConversationPreview>,
    emptyCopy: NearbyEmptyCopy,
    onOpenSaved: () -> Unit,
    onNearbyAction: () -> Unit,
    onPersonClick: (ConversationPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Nearby", color = MeshWhite, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "People around you",
                    color = MeshMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onOpenSaved) {
                Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Chats", tint = MeshWhite)
            }
        }
        if (people.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = emptyCopy.title,
                    color = MeshWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = emptyCopy.body,
                    color = MeshMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                MeshPrimaryButton(
                    text = emptyCopy.actionLabel,
                    modifier = Modifier.width(200.dp),
                    onClick = onNearbyAction
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
            ) {
                items(people, key = { it.id }) { person ->
                    NearbyPersonRow(person = person, onClick = { onPersonClick(person) })
                }
            }
        }
    }
}

@Composable
private fun NearbyPersonRow(
    person: ConversationPreview,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeshAvatar(name = person.name, size = 54.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.name,
                color = MeshWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = person.distanceLabel,
                color = MeshMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = person.subtitle.ifBlank { person.preview },
                color = MeshMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (person.online) MeshOnline else MeshAway)
        )
    }
}

@Composable
private fun ChatsPane(
    searchQuery: String,
    conversations: List<ConversationPreview>,
    onSearchChange: (String) -> Unit,
    onConversationClick: (ConversationPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    val chats = conversations.filter { it.hasMessages }
    Column(modifier.statusBarsPadding()) {
        Text(
            text = "Chats",
            color = MeshWhite,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp)
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .background(MeshSearchFill)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                cursorBrush = SolidColor(MeshWhite),
                textStyle = TextStyle(color = MeshWhite, fontSize = 15.sp),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) {
                        Text("Search...", color = MeshMuted, fontSize = 15.sp)
                    }
                    inner()
                }
            )
        }
        if (chats.isEmpty()) {
            Text(
                text = "No conversations yet. Tap someone nearby to start chatting.",
                color = MeshMuted,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(chats, key = { it.id }) { conversation ->
                    ChatInboxRow(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInboxRow(
    conversation: ConversationPreview,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeshAvatar(name = conversation.name, size = 54.dp)
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
            Text(
                text = conversation.preview,
                color = MeshMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        val timeLabel = formatInboxTime(conversation.timestamp)
        if (timeLabel.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(timeLabel, color = MeshMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.Top))
        }
    }
}

@Composable
private fun MeshBottomBar(
    selectedTab: MeshHomeTab,
    onTabChange: (MeshHomeTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeshBar)
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomItem("Nearby", Icons.Outlined.Place, selectedTab == MeshHomeTab.Nearby) {
            onTabChange(MeshHomeTab.Nearby)
        }
        BottomItem("Chats", Icons.AutoMirrored.Outlined.Chat, selectedTab == MeshHomeTab.Chats) {
            onTabChange(MeshHomeTab.Chats)
        }
        BottomItem("Profile", Icons.Outlined.Person, selectedTab == MeshHomeTab.Profile) {
            onTabChange(MeshHomeTab.Profile)
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) MeshAccent else MeshMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF070A14, widthDp = 390, heightDp = 844)
@Composable
private fun NearbyPreview() {
    MeshTheme {
        ChatsHomeScreen(
            searchQuery = "",
            conversations = listOf(
                ConversationPreview("Aarav", "Aarav", "Into tech, coffee and good conversations.", 1L, true, false, -52, 1, "Into tech, coffee and good conversations.", "~ 30 m away"),
                ConversationPreview("Diya", "Diya", "Book lover <3", 1L, true, false, -58, 1, "Book lover <3", "~ 50 m away"),
                ConversationPreview("Rohan", "Rohan", "Here for the event!", 0L, false, false, -66, 1, "Here for the event!", "~ 80 m away"),
                ConversationPreview("Meera", "Meera", "Music. Travel. Food.", 0L, true, false, -72, 1, "Music. Travel. Food.", "~ 120 m away"),
                ConversationPreview("Kabir", "Kabir", "Always down for a chat.", 0L, false, false, -76, 1, "Always down for a chat.", "~ 150 m away")
            ),
            selectedTab = MeshHomeTab.Nearby,
            onSearchChange = {},
            onTabChange = {},
            onStartMesh = {},
            onConversationClick = {},
            profile = {}
        )
    }
}
