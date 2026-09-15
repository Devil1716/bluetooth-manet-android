package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.devil1716.bluetoothmanet.ui.firstName
import com.devil1716.bluetoothmanet.ui.formatInboxTime
import com.devil1716.bluetoothmanet.ui.messageRequestsSubtitle
import com.devil1716.bluetoothmanet.ui.messageRequestsTitle
import com.devil1716.bluetoothmanet.ui.theme.AccentGradient
import com.devil1716.bluetoothmanet.ui.theme.HeroGradient
import com.devil1716.bluetoothmanet.ui.theme.HeroSearchFill
import com.devil1716.bluetoothmanet.ui.theme.MeshAccent
import com.devil1716.bluetoothmanet.ui.theme.MeshAway
import com.devil1716.bluetoothmanet.ui.theme.MeshBar
import com.devil1716.bluetoothmanet.ui.theme.MeshCard
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshNavy
import com.devil1716.bluetoothmanet.ui.theme.MeshOnline
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
    onStopMesh: () -> Unit = {},
    meshStarted: Boolean = false,
    /** Peers whose radio is in range; defaults to every conversation for previews. */
    nearbyPeople: List<ConversationPreview> = conversations,
    /** Threads with history, newest first; defaults to every conversation for previews. */
    chatThreads: List<ConversationPreview> = conversations,
    /** Peers waiting to be accepted into the inbox. */
    messageRequests: List<ConversationPreview> = emptyList(),
    onOpenRequests: () -> Unit = {},
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
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val offset = if (forward) 1 else -1
                    (slideInHorizontally(tween(260)) { width -> offset * width / 6 } +
                        fadeIn(tween(260)))
                        .togetherWith(
                            slideOutHorizontally(tween(200)) { width -> -offset * width / 6 } +
                                fadeOut(tween(160))
                        )
                },
                label = "homeTab",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    MeshHomeTab.Nearby -> NearbyPane(
                        people = nearbyPeople,
                        emptyCopy = nearbyEmpty,
                        meshStarted = meshStarted,
                        onNearbyAction = onNearbyAction,
                        onToggleMesh = { if (meshStarted) onStopMesh() else onNearbyAction() },
                        onPersonClick = onConversationClick,
                        modifier = Modifier.fillMaxSize()
                    )
                    MeshHomeTab.Chats -> ChatsPane(
                        searchQuery = searchQuery,
                        chats = chatThreads,
                        nearbyPeople = nearbyPeople,
                        requests = messageRequests,
                        meshStarted = meshStarted,
                        onSearchChange = onSearchChange,
                        onOpenRequests = onOpenRequests,
                        onAddClick = {
                            if (meshStarted) onTabChange(MeshHomeTab.Nearby)
                            else onNearbyAction()
                        },
                        onConversationClick = onConversationClick,
                        modifier = Modifier.fillMaxSize()
                    )
                    MeshHomeTab.Profile -> Box(Modifier.fillMaxSize()) { profile() }
                }
            }
            MeshBottomBar(selectedTab = selectedTab, onTabChange = onTabChange)
        }
    }
}

@Composable
private fun NearbyPane(
    people: List<ConversationPreview>,
    emptyCopy: NearbyEmptyCopy,
    meshStarted: Boolean,
    onNearbyAction: () -> Unit,
    onToggleMesh: () -> Unit,
    onPersonClick: (ConversationPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        ConnectedHero(
            title = "People\nNearby",
            searchQuery = "",
            onSearchChange = {},
            showSearch = false,
            stories = people,
            onAddClick = onToggleMesh,
            onStoryClick = onPersonClick,
            addLabel = if (meshStarted) "Stop" else "Start"
        )
        if (people.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                    textAlign = TextAlign.Center
                )
                MeshPrimaryButton(
                    text = emptyCopy.actionLabel,
                    modifier = Modifier.width(200.dp),
                    onClick = onNearbyAction
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
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
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeshAvatar(name = person.name, size = 54.dp, online = person.online)
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
    chats: List<ConversationPreview>,
    nearbyPeople: List<ConversationPreview>,
    requests: List<ConversationPreview>,
    meshStarted: Boolean,
    onSearchChange: (String) -> Unit,
    onOpenRequests: () -> Unit,
    onAddClick: () -> Unit,
    onConversationClick: (ConversationPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    val stories = nearbyPeople.ifEmpty { chats }
    Column(modifier) {
        ConnectedHero(
            title = "Let's Stay\nConnected",
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
            showSearch = true,
            stories = stories,
            onAddClick = onAddClick,
            onStoryClick = onConversationClick,
            addLabel = "Add"
        )
        AnimatedVisibility(
            visible = requests.isNotEmpty(),
            enter = expandVertically(tween(280)) + fadeIn(tween(280)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
        ) {
            MessageRequestsRow(requests = requests, onClick = onOpenRequests)
        }
        if (chats.isEmpty()) {
            Text(
                text = when {
                    requests.isNotEmpty() ->
                        "No accepted chats yet. Open your message requests to let someone in."
                    meshStarted ->
                        "No conversations yet. Tap someone nearby to start chatting."
                    else ->
                        "No conversations yet. Add someone nearby to start chatting."
                },
                color = MeshMuted,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)
            ) {
                items(chats, key = { it.id }) { conversation ->
                    ChatInboxRow(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(240),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            fadeOutSpec = tween(160)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Gradient header from the chat mock: search, a two-line title, and a
 * horizontal row of people with an Add control at the front.
 */
@Composable
private fun ConnectedHero(
    title: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    showSearch: Boolean,
    stories: List<ConversationPreview>,
    onAddClick: () -> Unit,
    onStoryClick: (ConversationPreview) -> Unit,
    addLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeroGradient)
            .statusBarsPadding()
            .padding(bottom = 18.dp)
    ) {
        if (showSearch) {
            Row(
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(HeroSearchFill)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MeshNavy.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Search" },
                        singleLine = true,
                        cursorBrush = SolidColor(MeshNavy),
                        textStyle = TextStyle(color = MeshNavy, fontSize = 15.sp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search...", color = MeshNavy.copy(alpha = 0.45f), fontSize = 15.sp)
                            }
                            inner()
                        }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = title,
            color = MeshWhite,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 38.sp,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "add") {
                StoryAddCell(label = addLabel, onClick = onAddClick)
            }
            items(stories, key = { it.id }) { person ->
                StoryPersonCell(person = person, onClick = { onStoryClick(person) })
            }
        }
    }
}

@Composable
private fun StoryAddCell(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .border(1.5.dp, MeshWhite.copy(alpha = 0.85f), CircleShape)
                .background(MeshWhite.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MeshWhite,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            color = MeshWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun StoryPersonCell(
    person: ConversationPreview,
    onClick: () -> Unit
) {
    val label = firstName(person.name).ifBlank { person.name }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = person.name }
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .border(2.dp, MeshWhite.copy(alpha = 0.55f), CircleShape)
                .padding(2.dp)
        ) {
            MeshAvatar(
                name = person.name,
                size = 54.dp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            text = label,
            color = MeshWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun MessageRequestsRow(
    requests: List<ConversationPreview>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MeshCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics {
                contentDescription = messageRequestsTitle(requests.size) + ". " +
                    messageRequestsSubtitle(requests.map { it.name })
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MeshAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MarkEmailUnread,
                contentDescription = null,
                tint = MeshAccent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = messageRequestsTitle(requests.size),
                color = MeshWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = messageRequestsSubtitle(requests.map { it.name }),
                color = MeshMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        AnimatedContent(
            targetState = requests.size,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f))
            },
            label = "requestBadge"
        ) { count ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.coerceAtMost(99).toString(),
                    color = MeshWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChatInboxRow(
    conversation: ConversationPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
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
            Text(
                text = conversation.preview,
                color = MeshMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        val timeLabel = formatInboxTime(conversation.timestamp)
        if (timeLabel.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = timeLabel,
                color = MeshMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Top).padding(top = 2.dp)
            )
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
private fun ChatsPreview() {
    val now = System.currentTimeMillis()
    MeshTheme {
        ChatsHomeScreen(
            searchQuery = "",
            conversations = listOf(
                ConversationPreview("Aarav", "Aarav", "Do you want to grab coffee this weekend?", now - 15_000L, true, false, -52, 1, distanceLabel = "~ 30 m away", hasMessages = true, nearby = true),
                ConversationPreview("Diya", "Diya", "Let me know if you need anything", now - 3 * 60 * 60 * 1000L, true, false, -58, 1, distanceLabel = "~ 50 m away", hasMessages = true, nearby = true),
                ConversationPreview("Rohan", "Rohan", "I'll be a little late, hope that's okay", now - 86_400_000L, false, false, -66, 1, distanceLabel = "~ 80 m away", hasMessages = true)
            ),
            selectedTab = MeshHomeTab.Chats,
            onSearchChange = {},
            onTabChange = {},
            onStartMesh = {},
            meshStarted = true,
            onConversationClick = {},
            profile = {}
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
                ConversationPreview("Aarav", "Aarav", "Into tech, coffee and good conversations.", 1L, true, false, -52, 1, "Into tech, coffee and good conversations.", "~ 30 m away", nearby = true),
                ConversationPreview("Diya", "Diya", "Book lover <3", 1L, true, false, -58, 1, "Book lover <3", "~ 50 m away", nearby = true),
                ConversationPreview("Rohan", "Rohan", "Here for the event!", 0L, false, false, -66, 1, "Here for the event!", "~ 80 m away", nearby = true)
            ),
            selectedTab = MeshHomeTab.Nearby,
            onSearchChange = {},
            onTabChange = {},
            onStartMesh = {},
            meshStarted = true,
            onConversationClick = {},
            profile = {}
        )
    }
}
