package com.payloadtunnel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.payloadtunnel.BuildConfig
import com.payloadtunnel.Graph
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.AppFilterMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier, onPickApps: () -> Unit) {
    val s by Graph.settings.state.collectAsState()
    val tunnel by TunnelStatus.state.collectAsState()
    var retries by remember { mutableStateOf(s.maxRetries.toString()) }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text("Reconnect", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                Modifier.fillMaxWidth().clickable { Graph.settings.update { it.copy(autoReconnect = !it.autoReconnect) } }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-reconnect")
                    Text(
                        "Reconnect automatically when the connection drops",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Per-app VPN", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (tunnel.status.isActive) {
                Text(
                    "Changes apply on the next connection",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppFilterMode.entries.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { Graph.settings.update { it.copy(appFilterMode = m) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.appFilterMode == m, onClick = { Graph.settings.update { it.copy(appFilterMode = m) } })
                    Text(m.label)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPickApps, enabled = s.appFilterMode != AppFilterMode.ALL, modifier = Modifier.fillMaxWidth()) {
                Text("Choose apps (${s.selectedApps.size} selected)")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("Payload Tunnel ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text(
                "SSH tunnel VPN with HTTP payload injection. tun2socks by hev-socks5-tunnel, SSH by JSch.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
