package com.payloadtunnel.ui

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.Graph
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import com.payloadtunnel.data.ShareNet
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    traffic: Traffic,
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: ((String?) -> Unit) -> Unit,
    onWatchAd: () -> Unit,
    adLoading: Boolean,
    onOpenAccount: () -> Unit,
) {
    val context = LocalContext.current
    val configs by Graph.configs.configs.collectAsState()
    val selectedId by Graph.configs.selectedId.collectAsState()
    val state by TunnelStatus.state.collectAsState()
    val sessionTime by Graph.session.timeRemaining.collectAsState()
    val announcement by Graph.session.announcement.collectAsState()
    val share by ShareNet.state.collectAsState()
    val selected = configs.firstOrNull { it.id == selectedId } ?: configs.firstOrNull()
    val active = state.status.isActive
    val connected = state.status == Status.CONNECTED

    // Local time countdown while connected (server syncs every ~30s).
    var base by remember { mutableLongStateOf(sessionTime) }
    var baseAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessionTime) { base = sessionTime; baseAt = System.currentTimeMillis() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(connected) { while (connected) { delay(1000); tick++ } }
    val shownTime = if (connected) (base - (System.currentTimeMillis() - baseAt) / 1000).coerceAtLeast(0) else base
    val outOfTime = shownTime <= 0

    var refreshing by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Outlined.Menu, "Account", onOpenAccount)
            Spacer(Modifier.weight(1f))
            Text("Jat Vpn", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            val spin = rememberInfiniteTransition(label = "r").animateFloat(
                0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "s",
            )
            CircleIconButton(
                Icons.Outlined.Refresh, "Refresh servers",
                onClick = {
                    if (!refreshing) {
                        refreshing = true
                        onRefresh { err ->
                            refreshing = false
                            Toast.makeText(context, err ?: "Servers updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = if (refreshing) Modifier.graphicsLayer { rotationZ = spin.value } else Modifier,
            )
        }

        if (announcement.isNotBlank()) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Lime.copy(alpha = 0.10f))
                    .border(1.dp, Lime.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(10.dp),
            ) { Text(announcement, color = Lime, fontSize = 12.sp) }
        }

        Spacer(Modifier.height(8.dp))
        // Time credit chip
        TimeChip(shownTime, outOfTime, onWatchAd)
        Spacer(Modifier.height(10.dp))
        StatusPill(state.status)
        state.message?.let {
            Text(it, color = if (state.status == Status.ERROR) Failed else TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp))
        }

        Spacer(Modifier.height(6.dp))
        PowerButton(
            status = state.status,
            enabled = selected != null && (!outOfTime || active),
            onClick = {
                when {
                    active -> onDisconnect()
                    outOfTime -> onWatchAd()
                    else -> selected?.let(onConnect)
                }
            },
        )

        if (outOfTime && !active) {
            PrimaryButton(
                text = "Watch ad for +${(Graph.session.adRewardSeconds / 60)} min",
                loading = adLoading,
                modifier = Modifier.padding(horizontal = 40.dp),
                onClick = onWatchAd,
            )
            Spacer(Modifier.height(12.dp))
        }

        // Selectors
        val entries = remember(configs) { Graph.configs.entries(configs) }
        ServerAndConfigSelectors(
            profiles = configs, entries = entries, selected = selected, enabled = !active,
            onSelectEntry = { Graph.configs.selectEntry(it) }, onSelectProfile = { Graph.configs.select(it) },
            hideDetails = true, modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (share.active && share.address != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                    .background(Lime.copy(alpha = 0.08f)).border(1.dp, Lime.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Wifi, null, tint = Lime, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sharing · PC proxy ${share.address}:${share.port}", color = TextPrimary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(Icons.Filled.ArrowDownward, "Download", if (active) formatSpeed(traffic.downSpeed) else "0 B/s", formatBytes(traffic.down), Modifier.weight(1f))
            MiniStat(Icons.Filled.ArrowUpward, "Upload", if (active) formatSpeed(traffic.upSpeed) else "0 B/s", formatBytes(traffic.up), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimeChip(seconds: Long, outOfTime: Boolean, onAdd: () -> Unit) {
    val color = if (outOfTime) Failed else if (seconds < 300) Working else Lime
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, color.copy(alpha = 0.4f), CircleShape).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Timer, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (outOfTime) "No time left" else "Time left  " + formatDuration(seconds * 1000),
            color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AddCircle, "Add time", tint = color, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun StatusPill(status: Status) {
    val (icon, color) = when (status) {
        Status.CONNECTED -> Icons.Filled.CheckCircle to Connected
        Status.ERROR -> Icons.Filled.ErrorOutline to Failed
        Status.DISCONNECTED -> Icons.Filled.PowerSettingsNew to TextSecondary
        else -> Icons.Filled.Sync to Working
    }
    Pill(status.label, icon = icon, iconTint = color, border = color.copy(alpha = 0.35f), background = color.copy(alpha = 0.08f))
}

@Composable
private fun PowerButton(status: Status, enabled: Boolean, onClick: () -> Unit) {
    val connected = status == Status.CONNECTED
    val busy = status.isActive && !connected
    val transition = rememberInfiniteTransition(label = "rings")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 2500 else 30000, easing = LinearEasing)), label = "spin")
    val pulse by transition.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    val glow by animateFloatAsState(if (connected) 1f else if (busy) 0.6f else 0.22f, tween(600), label = "glow")

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val rOuter = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Lime.copy(alpha = 0.28f * glow * (if (busy) pulse else 1f)), Color.Transparent), center = c, radius = rOuter), radius = rOuter, center = c)
            rotate(spin, c) {
                val lines = 170
                for (i in 0 until lines) {
                    val a = (i * 2 * PI / lines).toFloat()
                    val wobble = ((sin(i * 0.9) + 1) / 2).toFloat()
                    val r1 = rOuter * 0.56f
                    val r2 = rOuter * (0.70f + 0.16f * wobble)
                    val alpha = (0.10f + 0.35f * wobble) * (0.35f + 0.65f * glow)
                    drawLine(LimeDim.copy(alpha = alpha), Offset(c.x + r1 * cos(a), c.y + r1 * sin(a)), Offset(c.x + r2 * cos(a), c.y + r2 * sin(a)), strokeWidth = 1.2f, cap = StrokeCap.Round)
                }
            }
            drawCircle(Lime.copy(alpha = 0.25f + 0.35f * glow), radius = rOuter * 0.55f, center = c, style = Stroke(2f))
        }
        val fill = if (connected) Lime else if (busy) Lime.copy(alpha = 0.75f) else Color(0xFF263019)
        val fg = if (connected || busy) OnLime else Lime
        Box(
            Modifier.size(140.dp).clip(CircleShape)
                .background(if (connected || busy) Brush.radialGradient(listOf(Color(0xFFE6FF6B), fill)) else Brush.radialGradient(listOf(Color(0xFF34421F), fill)))
                .border(2.dp, Lime.copy(alpha = if (connected) 0f else 0.5f), CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PowerSettingsNew, null, tint = fg, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        connected -> "Disconnect"
                        status == Status.DISCONNECTING -> "Stopping…"
                        busy -> "Cancel"
                        else -> "Connect"
                    },
                    color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, label: String, speed: String, total: String, modifier: Modifier) {
    GlassCard(modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Lime.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Lime, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(speed, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("$label · $total", color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
