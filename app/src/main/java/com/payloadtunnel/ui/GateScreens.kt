package com.payloadtunnel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.Graph
import com.payloadtunnel.data.Session

@Composable
private fun Logo(size: Int = 92) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Lime, LimeDim)))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Bolt, null, tint = OnLime, modifier = Modifier.size((size * 0.5).dp)) }
}

@Composable
fun LoadingScreen() {
    AppBackground {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Logo(72)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = Lime, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text("Loading servers…", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun BlockedScreen(deviceId: String) {
    AppBackground {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Logo(72)
            Spacer(Modifier.height(20.dp))
            Text("Access blocked", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("ID ${deviceId.take(8)}", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    AppBackground {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Logo(72)
            Spacer(Modifier.height(20.dp))
            Text("Can't reach the server", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Retry", onClick = onRetry)
        }
    }
}
