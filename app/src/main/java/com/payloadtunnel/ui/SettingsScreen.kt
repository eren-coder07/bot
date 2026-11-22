package com.payloadtunnel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.BuildConfig
import com.payloadtunnel.Graph
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.AppFilterMode

@Composable
fun SettingsScreen(onBack: () -> Unit, onPickApps: () -> Unit) {
    val s by Graph.settings.state.collectAsState()
    val tunnel by TunnelStatus.state.collectAsState()
    var retries by remember { mutableStateOf(s.maxRetries.toString()) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", left = { CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Reconnect")
                Row(
                    Modifier.fillMaxWidth().clickable { Graph.settings.update { it.copy(autoReconnect = !it.autoReconnect) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-reconnect", color = TextPrimary)
                        Text("Reconnect automatically when the connection drops", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = s.autoReconnect, onCheckedChange = { v -> Graph.settings.update { it.copy(autoReconnect = v) } })
                }
                OutlinedTextField(
                    value = retries,
                    onValueChange = { v ->
                        retries = v.filter(Char::isDigit).take(4)
                        Graph.settings.update { it.copy(maxRetries = retries.toIntOrNull() ?: 0) }
                    },
                    enabled = s.autoReconnect,
                    label = { Text("Retry limit") },
                    supportingText = { Text(if ((retries.toIntOrNull() ?: 0) == 0) "0 = unlimited" else "Stop after this many failed attempts") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Per-app VPN")
                if (tunnel.status.isActive) {
                    Text("Changes apply on the next connection", color = TextSecondary, fontSize = 12.sp)
                }
                AppFilterMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Graph.settings.update { it.copy(appFilterMode = m) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.appFilterMode == m, onClick = { Graph.settings.update { it.copy(appFilterMode = m) } })
                        Text(m.label, color = TextPrimary, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onPickApps, enabled = s.appFilterMode != AppFilterMode.ALL,
                    shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = OnLime),
                ) { Text("Choose apps (${s.selectedApps.size} selected)") }
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("About")
                Text("Payload Tunnel ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = TextPrimary, fontSize = 14.sp)
                Text(
                    "SSH tunnel VPN with HTTP payload injection. tun2socks by hev-socks5-tunnel, SSH by JSch.",
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
