package com.devil1716.bluetoothmanet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ui.avatarColor
import com.devil1716.bluetoothmanet.ui.avatarInitials
import com.devil1716.bluetoothmanet.ui.theme.MeshNavy
import com.devil1716.bluetoothmanet.ui.theme.MeshOnline
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@Composable
fun MeshAvatar(
    name: String,
    size: Dp,
    online: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = if (online) "$name, online" else name },
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(avatarColor(name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarInitials(name),
                color = MeshWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.34f).sp
            )
        }
        if (online) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(MeshOnline)
                    .border(2.dp, MeshNavy, CircleShape)
            )
        }
    }
}
