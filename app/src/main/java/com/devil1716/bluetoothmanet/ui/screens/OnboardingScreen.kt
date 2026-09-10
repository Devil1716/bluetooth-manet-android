package com.devil1716.bluetoothmanet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ui.components.MeshAtmosphere
import com.devil1716.bluetoothmanet.ui.components.MeshMark
import com.devil1716.bluetoothmanet.ui.components.MeshPrimaryButton
import com.devil1716.bluetoothmanet.ui.components.MeshWordmark
import com.devil1716.bluetoothmanet.ui.theme.MeshMuted
import com.devil1716.bluetoothmanet.ui.theme.MeshTheme
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onGetStarted: () -> Unit
) {
    MeshAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Text(
            text = "Skip",
            color = MeshMuted,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 24.dp)
                .clickable(onClick = onSkip)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.22f))
            MeshMark(Modifier.size(148.dp))
            Spacer(Modifier.height(28.dp))
            MeshWordmark(fontSize = 32.sp, letterSpacing = 12.dp)
            Spacer(Modifier.height(36.dp))
            Text(
                text = "People Nearby.\nReal Conversations.",
                color = MeshWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Discover and chat with people around you\nusing local device connection.",
                color = MeshMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.weight(0.28f))
            MeshPrimaryButton(
                text = "Get Started",
                modifier = Modifier
                    .width(240.dp)
                    .padding(bottom = 36.dp),
                onClick = onGetStarted
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF070A14, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingPreview() {
    MeshTheme {
        Box(Modifier.fillMaxWidth()) {
            OnboardingScreen(onSkip = {}, onGetStarted = {})
        }
    }
}
