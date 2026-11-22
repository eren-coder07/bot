package com.payloadtunnel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.BuildConfig
import com.payloadtunnel.Graph

/** Bottom sheet with the account summary: masked key, time left, add time, switch key. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(onDismiss: () -> Unit, onWatchAd: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Card, contentColor = TextPrimary) {
        AccountSheetContent(
            onWatchAd = onWatchAd,
            onUseDifferentKey = {
                Graph.session.clearKey()
                onDismiss()
            },
        )
    }
}

/** The sheet's body, split out so it can be rendered/previewed without the modal window. */
@Composable
fun AccountSheetContent(onWatchAd: () -> Unit, onUseDifferentKey: () -> Unit) {
    val key by Graph.session.key.collectAsState()
    val timeRemaining by Graph.session.timeRemaining.collectAsState()

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Text("Account", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                Graph.session.label.ifBlank { "Access key" },
                color = TextSecondary, fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                maskKey(key),
                color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = Lime, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${formatDuration(timeRemaining * 1000)} left",
                    color = Lime, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = onWatchAd,
            shape = RoundedCornerShape(14.dp),
            color = Lime,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = OnLime, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Add time — watch an ad",
                    color = OnLime, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = onUseDifferentKey,
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.04f),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Use a different key", color = TextPrimary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Payload Tunnel v${BuildConfig.VERSION_NAME}",
            color = TextSecondary, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** Hides most of the key, e.g. "ab12••••ef56". */
fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return "No key"
    return if (key.length >= 10) "${key.take(4)}••••${key.takeLast(4)}" else "••••${key.takeLast(2)}"
}
