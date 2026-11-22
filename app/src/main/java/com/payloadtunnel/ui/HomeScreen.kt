package com.payloadtunnel.ui

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    traffic: Traffic,
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenServers: () -> Unit,
) {
    val profiles by Graph.profiles.profiles.collectAsState()
    val selectedId by Graph.profiles.selectedId.collectAsState()
    val state by TunnelStatus.state.collectAsState()
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val active = state.status.isActive
    val connected = state.status == Status.CONNECTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: menu · mode pill · speed
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Outlined.Menu, "Menu", onOpenMenu)
            Spacer(Modifier.weight(1f))
            Pill(
                selected?.mode?.label?.removePrefix("SSH-")?.removePrefix("SSH ") ?: "SSH",
                icon = Icons.Filled.Bolt, iconTint = Color(0xFFFFC93C),
                modifier = Modifier.padding(end = 8.dp), onClick = onOpenServers,
            )
            CircleIconButton(Icons.Outlined.Speed, "Speed test", onOpenSpeed)
        }

        Spacer(Modifier.height(18.dp))
        StatusPill(state.status)
        Spacer(Modifier.height(10.dp))
        Text(
            if (connected && state.connectedAt > 0) formatDuration(traffic.now - state.connectedAt) else "00:00:00",
            color = TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Normal, letterSpacing = 1.sp,
        )
        val msg = state.message
        Text(
            msg ?: if (connected) "Connected to ${state.profileName ?: selected?.name ?: ""}" else selected?.name ?: "No profile",
            color = if (state.status == Status.ERROR) Failed else TextSecondary,
            fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 3,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(8.dp))
        PowerButton(
            status = state.status,
            onClick = { if (active) onDisconnect() else selected?.let(onConnect) },
        )

        // Live traffic
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniStat(Icons.Filled.ArrowDownward, "Download", if (active) formatSpeed(traffic.downSpeed) else "0 B/s", formatBytes(traffic.down), Modifier.weight(1f))
            MiniStat(Icons.Filled.ArrowUpward, "Upload", if (active) formatSpeed(traffic.upSpeed) else "0 B/s", formatBytes(traffic.up), Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        // Server cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(profiles, key = { it.id }) { p ->
                ServerCard(
                    profile = p,
                    isSelected = p.id == selected?.id,
                    isActive = active && p.id == selected?.id,
                    locked = active,
                    onSelect = { Graph.profiles.select(p.id) },
                    onConnect = {
                        Graph.profiles.select(p.id)
                        onConnect(p)
                    },
                )
            }
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
private fun PowerButton(status: Status, onClick: () -> Unit) {
    val connected = status == Status.CONNECTED
    val busy = status.isActive && !connected
    val transition = rememberInfiniteTransition(label = "rings")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 2500 else 30000, easing = LinearEasing)), label = "spin")
    val pulse by transition.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    val glow by animateFloatAsState(if (connected) 1f else if (busy) 0.6f else 0.25f, tween(600), label = "glow")

    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
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
        val fill = if (connected) Lime else if (busy) Lime.copy(alpha = 0.75f) else Color(0xFF263019)
        val fg = if (connected || busy) OnLime else Lime
        Box(
            Modifier.size(156.dp).clip(CircleShape)
                .background(
                    if (connected || busy) Brush.radialGradient(listOf(Color(0xFFE6FF6B), fill))
                    else Brush.radialGradient(listOf(Color(0xFF34421F), fill)),
                )
                .border(2.dp, Lime.copy(alpha = if (connected) 0.0f else 0.5f), CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
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

@Composable
private fun ServerCard(
    profile: Profile,
    isSelected: Boolean,
    isActive: Boolean,
    locked: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
) {
    GlassCard(
        Modifier.width(158.dp).clickable(enabled = !locked, onClick = onSelect),
        highlighted = isSelected,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile.name, 34.dp)
            Spacer(Modifier.weight(1f))
            Pill(
                if (isActive) "Active" else "Connect",
                textColor = if (isActive) OnLime else TextPrimary,
                background = if (isActive) Lime else Color.White.copy(alpha = 0.06f),
                border = if (isActive) Lime else Color.White.copy(alpha = 0.16f),
                onClick = if (locked) null else onConnect,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(profile.mode.label, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            profile.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
