package com.payloadtunnel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.data.Profile

private enum class Tab(val label: String, val icon: ImageVector) {
    VPN("VPN", Icons.Outlined.Bolt),
    SPEED("Speed", Icons.Outlined.Speed),
    LOGS("Logs", Icons.AutoMirrored.Outlined.ReceiptLong),
}

@Composable
fun MainScaffold(
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: ((String?) -> Unit) -> Unit,
    onWatchAd: () -> Unit,
    adLoading: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.VPN) }
    var account by rememberSaveable { mutableStateOf(false) }
    val traffic = rememberTraffic()

    AppBackground {
        if (account) {
            BackHandler { account = false }
            AccountScreen(onBack = { account = false }, onWatchAd = onWatchAd, adLoading = adLoading, onRefresh = onRefresh)
            return@AppBackground
        }
        if (tab != Tab.VPN) BackHandler { tab = Tab.VPN }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.VPN -> HomeScreen(traffic, onConnect, onDisconnect, onRefresh, onWatchAd, adLoading) { account = true }
                    Tab.SPEED -> SpeedScreen(traffic) { tab = Tab.VPN }
                    Tab.LOGS -> LogScreen()
                }
            }
            BottomBar(tab) { tab = it }
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF12170D), Color(0xFF080B06))))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { t ->
                val active = t == selected
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).clickable { onSelect(t) }.padding(horizontal = 22.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(if (active) Lime.copy(alpha = 0.16f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, t.label, tint = if (active) Lime else TextSecondary, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, color = if (active) Lime else TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
