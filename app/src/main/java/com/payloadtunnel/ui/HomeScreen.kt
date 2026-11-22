package com.payloadtunnel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.Graph
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier,
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    onEditProfile: (Profile) -> Unit,
) {
    val profiles by Graph.profiles.profiles.collectAsState()
    val selectedId by Graph.profiles.selectedId.collectAsState()
    val state by TunnelStatus.state.collectAsState()
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val active = state.status.isActive

    // Live counters sampled every second.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var up by remember { mutableLongStateOf(0L) }
    var down by remember { mutableLongStateOf(0L) }
    var upSpeed by remember { mutableLongStateOf(0L) }
    var downSpeed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var lastUp = TunnelStatus.bytesUp.get()
        var lastDown = TunnelStatus.bytesDown.get()
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
            val u = TunnelStatus.bytesUp.get()
            val d = TunnelStatus.bytesDown.get()
            upSpeed = (u - lastUp).coerceAtLeast(0)
            downSpeed = (d - lastDown).coerceAtLeast(0)
            lastUp = u; lastDown = d
            up = u; down = d
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Payload Tunnel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        // Profile picker
        var expanded by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!active) expanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selected?.name ?: "No profile",
                    onValueChange = {},
                    readOnly = true,
                    enabled = !active,
                    label = { Text("Profile") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    profiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                Graph.profiles.select(p.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
            if (selected != null) {
                IconButton(onClick = { onEditProfile(selected) }, enabled = !active) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit profile")
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Big connect button
        val color by animateColorAsState(
            when (state.status) {
                Status.CONNECTED -> Connected
                Status.ERROR -> Failed
                Status.DISCONNECTED -> MaterialTheme.colorScheme.primary
                else -> Working
            },
            label = "btn",
        )
        Surface(
            onClick = {
                if (active) onDisconnect() else selected?.let(onConnect)
            },
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            border = BorderStroke(4.dp, color),
            modifier = Modifier.size(190.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PowerSettingsNew, null, tint = color, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (active) "DISCONNECT" else "CONNECT",
                        color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(state.status.label, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        state.message?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it, textAlign = TextAlign.Center,
                color = if (state.status == Status.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.status == Status.CONNECTED && state.connectedAt > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                formatDuration(now - state.connectedAt),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Filled.ArrowUpward, "Upload", if (active) formatSpeed(upSpeed) else "—", formatBytes(up), Modifier.weight(1f))
            StatCard(Icons.Filled.ArrowDownward, "Download", if (active) formatSpeed(downSpeed) else "—", formatBytes(down), Modifier.weight(1f))
        }

        if (selected != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    InfoRow("Mode", selected.mode.label)
                    InfoRow("SSH", "${selected.sshHost}:${selected.sshPort}")
                    if (selected.mode.usesProxy && selected.proxy.isNotBlank()) InfoRow("Proxy", selected.proxy)
                    InfoRow("User", selected.username)
                    val port = if (state.status == Status.CONNECTED && state.socksPort > 0) state.socksPort else selected.localSocksPort
                    InfoRow("SOCKS5", "127.0.0.1:$port")
                    InfoRow("DNS", if (selected.remoteDns) "Remote (SSH server)" else selected.dnsServers.joinToString())
                    state.serverVersion?.takeIf { state.status == Status.CONNECTED }?.let { InfoRow("Server", it) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, title: String, speed: String, total: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(6.dp))
            Text(speed, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Total $total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
