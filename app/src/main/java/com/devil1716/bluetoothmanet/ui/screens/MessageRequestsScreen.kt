package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ui.ConversationPreview
import com.devil1716.bluetoothmanet.ui.components.MeshAtmosphere
import com.devil1716.bluetoothmanet.ui.components.MeshAvatar
import com.devil1716.bluetoothmanet.ui.formatInboxTime
import com.devil1716.bluetoothmanet.ui.messageRequestsTitle
import com.devil1716.bluetoothmanet.ui.requestsEmptyBody
import com.devil1716.bluetoothmanet.ui.theme.AccentGradient
import com.devil1716.bluetoothmanet.ui.theme.MeshAccent
import com.devil1716.bluetoothmanet.ui.theme.MeshCard
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshTheme
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@Composable
fun MessageRequestsScreen(
    requests: List<ConversationPreview>,
    onBack: () -> Unit,
    onOpen: (ConversationPreview) -> Unit,
    onAccept: (ConversationPreview) -> Unit,
    onDecline: (ConversationPreview) -> Unit
) {
    MeshAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeshWhite
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Message requests",
                        color = MeshWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // The count animates so accepting or deleting reads as a
                    // change rather than a silent relabel.
                    AnimatedContent(
                        targetState = requests.size,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f))
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "requestCount"
                    ) { count ->
                        Text(
                            text = if (count == 0) "All caught up" else messageRequestsTitle(count),
                            color = MeshMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = requests.isEmpty(),
                transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(160))) },
                label = "requestsBody",
                modifier = Modifier.weight(1f)
            ) { empty ->
                if (empty) {
                    RequestsEmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(requests, key = { it.id }) { request ->
                            RequestCard(
                                request = request,
                                onOpen = { onOpen(request) },
                                onAccept = { onAccept(request) },
                                onDecline = { onDecline(request) },
                                // Rows slide and fade as the queue shrinks, so
                                // the list settling is visible rather than a jump.
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(220),
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
    }
}

@Composable
private fun RequestsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.MarkEmailUnread,
            contentDescription = null,
            tint = MeshAccent,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = "No requests waiting",
            color = MeshWhite,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = requestsEmptyBody(),
            color = MeshMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun RequestCard(
    request: ConversationPreview,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MeshCard)
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MeshAvatar(name = request.name, size = 46.dp, online = request.online)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = request.name,
                    color = MeshWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = request.distanceLabel,
                    color = MeshMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            val time = formatInboxTime(request.timestamp)
            if (time.isNotEmpty()) {
                Text(time, color = MeshMuted, fontSize = 12.sp)
            }
        }
        Text(
            text = request.preview,
            color = MeshWhite.copy(alpha = 0.82f),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RequestAction(
                text = "Accept",
                onClick = onAccept,
                accent = true,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Accept request from ${request.name}" }
            )
            RequestAction(
                text = "Delete",
                onClick = onDecline,
                accent = false,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Delete request from ${request.name}" }
            )
        }
    }
}

/** A pill button that dips slightly while held, to confirm the tap landed. */
@Composable
private fun RequestAction(
    text: String,
    onClick: () -> Unit,
    accent: Boolean,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "actionScale"
    )
    Box(
        modifier = modifier
            .scale(scale.value)
            .height(42.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (accent) Modifier.background(AccentGradient)
                else Modifier.background(MeshWhite.copy(alpha = 0.10f))
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (accent) MeshWhite else MeshMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF070A14, widthDp = 390, heightDp = 844)
@Composable
private fun MessageRequestsPreview() {
    MeshTheme {
        MessageRequestsScreen(
            requests = listOf(
                ConversationPreview(
                    id = "K7M2",
                    name = "K7M2",
                    preview = "Hey, I'm at the north gate if you need a hand.",
                    timestamp = System.currentTimeMillis() - 60_000L,
                    online = true,
                    hasAttachment = false,
                    distanceLabel = "~ 30 m away",
                    hasMessages = true,
                    nearby = true,
                    pendingRequest = true
                ),
                ConversationPreview(
                    id = "B4Q1",
                    name = "B4Q1",
                    preview = "Anyone got signal out here?",
                    timestamp = System.currentTimeMillis() - 3_600_000L,
                    online = false,
                    hasAttachment = false,
                    distanceLabel = "~ 80 m away",
                    hasMessages = true,
                    pendingRequest = true
                )
            ),
            onBack = {},
            onOpen = {},
            onAccept = {},
            onDecline = {}
        )
    }
}
