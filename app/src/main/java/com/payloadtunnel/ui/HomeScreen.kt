package com.payloadtunnel.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.Graph
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    traffic: Traffic,
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSpeed: () -> Unit,
    onWatchAd: () -> Unit,
) {
    val configs by Graph.configs.configs.collectAsState()
    val selectedId by Graph.configs.selectedId.collectAsState()
    val serverTime by Graph.session.timeRemaining.collectAsState()
    val announcement by Graph.session.announcement.collectAsState()
    val state by TunnelStatus.state.collectAsState()
    val adMinutes = remember { Graph.session.adRewardSeconds / 60 }

    val selected = configs.firstOrNull { it.id == selectedId } ?: configs.firstOrNull()
    val active = state.status.isActive
    val connected = state.status == Status.CONNECTED

    // Time left: ticks locally every second while connected; re-syncs whenever the
    // server answers (auth, heartbeat, ad reward).
    var timeLeft by remember { mutableLongStateOf(serverTime) }
    LaunchedEffect(serverTime) { timeLeft = serverTime }
    LaunchedEffect(connected) {
        while (connected) {
            delay(1000)
            if (timeLeft > 0) timeLeft--
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: account · speed
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Outlined.Menu, "Account", onOpenAccount)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Outlined.Speed, "Speed test", onOpenSpeed)
        }

        // Announcement banner (set from the admin panel)
        if (announcement.isNotBlank()) {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Campaign, null, tint = Lime, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(announcement, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        StatusPill(state.status)
        Spacer(Modifier.height(10.dp))
        Text("TIME LEFT", color = TextSecondary, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Text(
            formatDuration(timeLeft * 1000),
            color = if (timeLeft <= 0) Failed else TextPrimary,
            fontSize = 44.sp, fontWeight = FontWeight.Normal, letterSpacing = 1.sp,
        )
        val msg = state.message
        Text(
            msg ?: when {
                connected -> "Connected to ${state.profileName ?: selected?.name ?: ""}"
                selected != null -> selected.name
                else -> "No server loaded yet"
            },
            color = if (state.status == Status.ERROR) Failed else TextSecondary,
            fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 3,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(8.dp))
        PowerButton(
            status = state.status,
            enabled = timeLeft > 0,
            onClick = { if (active) onDisconnect() else selected?.let(onConnect) },
        )

        // Time-credit actions: prominent ad button when out of time, small one otherwise.
        if (timeLeft <= 0) {
            Spacer(Modifier.height(6.dp))
            WatchAdButton(
                text = "Watch ad (+$adMinutes min)",
                onClick = onWatchAd,
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(2.dp))
            Pill("＋ Add time", icon = Icons.Filled.Add, onClick = onWatchAd)
        }

        // Server + config pickers: names only, no hosts/modes/payloads in the user UI.
        val entries = remember(configs) { Graph.configs.entries(configs) }
        ServerAndConfigSelectors(
            profiles = configs,
            entries = entries,
            selected = selected,
            enabled = !active,
            hideDetails = true,
            onSelectEntry = { Graph.configs.selectEntry(it) },
            onSelectProfile = { Graph.configs.select(it) },
            modifier = Modifier.padding(horizontal = 16.dp),
            serverTrailing = {
                Text(
                    "↑ ${formatBytes(traffic.up)}  ↓ ${formatBytes(traffic.down)}",
                    color = TextSecondary, fontSize = 12.sp,
                )
            },
        )

        Spacer(Modifier.height(14.dp))
        // Live traffic
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniStat(Icons.Filled.ArrowDownward, "Download", if (active) formatSpeed(traffic.downSpeed) else "0 B/s", formatBytes(traffic.down), Modifier.weight(1f))
            MiniStat(Icons.Filled.ArrowUpward, "Upload", if (active) formatSpeed(traffic.upSpeed) else "0 B/s", formatBytes(traffic.up), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
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
    Pill(
        status.label, icon = icon, iconTint = color,
        border = color.copy(alpha = 0.35f), background = color.copy(alpha = 0.08f),
    )
}

@Composable
private fun PowerButton(status: Status, enabled: Boolean = true, onClick: () -> Unit) {
    val connected = status == Status.CONNECTED
    val busy = status.isActive && !connected
    val transition = rememberInfiniteTransition(label = "rings")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 2500 else 30000, easing = LinearEasing)), label = "spin")
    val pulse by transition.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    val glow by animateFloatAsState(
        if (connected) 1f else if (busy) 0.6f else if (enabled) 0.25f else 0.12f,
        tween(600), label = "glow",
    )

    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val rOuter = size.minDimension / 2
            // soft glow
            drawCircle(
                Brush.radialGradient(
                    listOf(Lime.copy(alpha = 0.28f * glow * (if (busy) pulse else 1f)), Color.Transparent),
                    center = c, radius = rOuter,
                ),
                radius = rOuter, center = c,
            )
            // sunburst rings: many fine radial lines with varying length
            rotate(spin, c) {
                val lines = 180
                for (i in 0 until lines) {
                    val a = (i * 2 * PI / lines).toFloat()
                    val wobble = ((sin(i * 0.9) + 1) / 2).toFloat()
                    val r1 = rOuter * 0.56f
                    val r2 = rOuter * (0.70f + 0.16f * wobble)
                    val alpha = (0.10f + 0.35f * wobble) * (0.35f + 0.65f * glow)
                    drawLine(
                        LimeDim.copy(alpha = alpha),
                        Offset(c.x + r1 * cos(a), c.y + r1 * sin(a)),
                        Offset(c.x + r2 * cos(a), c.y + r2 * sin(a)),
                        strokeWidth = 1.2f, cap = StrokeCap.Round,
                    )
                }
            }
            // thin ring around the button
            drawCircle(Lime.copy(alpha = 0.25f + 0.35f * glow), radius = rOuter * 0.55f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        val dimmed = !enabled && !busy && !connected
        val fill = if (connected) Lime else if (busy) Lime.copy(alpha = 0.75f) else if (dimmed) Color(0xFF1B2213) else Color(0xFF263019)
        val fg = if (connected || busy) OnLime else Lime.copy(alpha = if (dimmed) 0.45f else 1f)
        Box(
            Modifier.size(140.dp).clip(CircleShape)
                .background(
                    if (connected || busy) Brush.radialGradient(listOf(Color(0xFFE6FF6B), fill))
                    else Brush.radialGradient(listOf(if (dimmed) Color(0xFF232B17) else Color(0xFF34421F), fill)),
                )
                .border(2.dp, Lime.copy(alpha = if (connected || dimmed) 0.0f else 0.5f), CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PowerSettingsNew, null, tint = fg, modifier = Modifier.size(44.dp))
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

/** The prominent lime call to action shown when the user is out of time. */
@Composable
private fun WatchAdButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Lime,
        border = BorderStroke(1.dp, LimeDeep),
        modifier = modifier.height(52.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.OndemandVideo, null, tint = OnLime, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = OnLime, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, speed: String, total: String, modifier: Modifier) {
    GlassCard(modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Lime.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Lime, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(speed, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Default)
                Text("$label · $total", color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
