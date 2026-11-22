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
fun KeyGateScreen(state: Session.State, onSubmit: (String) -> Unit) {
    val announcement by Graph.session.announcement.collectAsState()
    var key by remember { mutableStateOf(Graph.session.key.value ?: "") }
    val checking = state == Session.State.CHECKING
    val error = when (state) {
        Session.State.INVALID -> "That access key isn't valid. Check it and try again."
        Session.State.BANNED -> "This access key has been blocked."
        Session.State.OFFLINE -> "Can't reach the server. Check your connection and retry."
        else -> null
    }

    AppBackground {
        Column(
            Modifier.fillMaxSize().imePadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Logo()
            Spacer(Modifier.height(18.dp))
            Text("Jat Vpn", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("just another tunnel vpn", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(36.dp))
            Text(
                "Enter your access key to continue",
                color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, if (error != null) Failed.copy(alpha = 0.6f) else CardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.VpnKey, null, tint = Lime, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (key.isEmpty()) Text("XXXX-XXXX-XXXX", color = TextSecondary, fontSize = 16.sp)
                    BasicTextField(
                        value = key,
                        onValueChange = { key = it.uppercase() },
                        singleLine = true,
                        enabled = !checking,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Lime),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (key.isNotBlank()) onSubmit(key.trim()) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            AnimatedVisibility(error != null) {
                Text(error ?: "", color = Failed, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (state == Session.State.OFFLINE) "Retry" else "Connect",
                loading = checking,
                enabled = key.isNotBlank() && !checking,
                onClick = { onSubmit(key.trim()) },
            )
            if (announcement.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text(announcement, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Text("No key? Ask the admin for one.", color = TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
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
