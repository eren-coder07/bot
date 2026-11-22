package com.payloadtunnel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.payloadtunnel.core.Payload
import com.payloadtunnel.data.Profile
import com.payloadtunnel.data.TunnelMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditor(initial: Profile, onSave: (Profile) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.sshHost) }
    var port by remember { mutableStateOf(initial.sshPort.toString()) }
    var user by remember { mutableStateOf(initial.username) }
    var pass by remember { mutableStateOf(initial.password) }
    var showPass by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(initial.mode) }
    var proxy by remember { mutableStateOf(initial.proxy) }
    var payload by remember { mutableStateOf(TextFieldValue(initial.payload)) }
    var socksPort by remember { mutableStateOf(initial.localSocksPort.toString()) }
    var dns by remember { mutableStateOf(initial.dns) }
    var remoteDns by remember { mutableStateOf(initial.remoteDns) }
    var attempted by remember { mutableStateOf(false) }

    val portOk = port.toIntOrNull() in 1..65535
    val socksOk = socksPort.toIntOrNull() in 1..65535
    val proxyOk = proxy.isBlank() || Profile.parseHostPort(proxy) != null
    val valid = name.isNotBlank() && host.isNotBlank() && portOk && socksOk && proxyOk

    fun save() {
        attempted = true
        if (!valid) return
        onSave(
            initial.copy(
                name = name.trim(), sshHost = host.trim(), sshPort = port.toInt(),
                username = user.trim(), password = pass, mode = mode, proxy = proxy.trim(),
                payload = payload.text, localSocksPort = socksPort.toInt(),
                dns = dns.trim().ifEmpty { Profile.DEFAULT_DNS },
                remoteDns = remoteDns,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial.sshHost.isEmpty()) "New profile" else "Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") }
                },
                actions = {
                    IconButton(onClick = ::save) { Icon(Icons.Filled.Check, "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                name, { name = it }, label = { Text("Profile name") }, singleLine = true,
                isError = attempted && name.isBlank(), modifier = Modifier.fillMaxWidth(),
            )

            var modeOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = modeOpen, onExpandedChange = { modeOpen = it }) {
                OutlinedTextField(
                    value = mode.label, onValueChange = {}, readOnly = true, label = { Text("Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = modeOpen, onDismissRequest = { modeOpen = false }) {
                    TunnelMode.entries.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { mode = m; modeOpen = false })
                    }
                }
            }

            Section("SSH server")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    host, { host = it }, label = { Text("SSH host") }, singleLine = true,
                    isError = attempted && host.isBlank(), modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                    isError = !portOk, modifier = Modifier.weight(0.45f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show password")
                    }
                },
            )

            if (mode.usesProxy) {
                Section("Remote proxy")
                OutlinedTextField(
                    proxy, { proxy = it }, label = { Text("Proxy host:port (optional)") }, singleLine = true,
                    isError = !proxyOk, placeholder = { Text("help.twitter.com:8080") },
                    supportingText = { Text("Leave empty to connect straight to the SSH host") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }

            if (mode.usesPayload) {
                Section("Payload")
                OutlinedTextField(
                    payload, { payload = it }, label = { Text("Payload") },
                    minLines = 5, maxLines = 12, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    supportingText = {
                        Text(
                            if (mode == TunnelMode.SSH_PROXY) "Empty = CONNECT [host_port] HTTP/1.1. Use [crlf] for line breaks."
                            else "Use [crlf] for line breaks; real line breaks are ignored.",
                        )
                    },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Payload.TAGS.forEach { tag ->
                        AssistChip(onClick = {
                            val t = payload.text
                            val start = payload.selection.min
                            val end = payload.selection.max
                            val newText = t.substring(0, start) + tag + t.substring(end)
                            payload = TextFieldValue(newText, TextRange(start + tag.length))
                        }, label = { Text(tag) })
                    }
                }
            }

            Section("Local")
            OutlinedTextField(
                socksPort, { socksPort = it.filter(Char::isDigit).take(5) }, label = { Text("Local SOCKS5 port") },
                singleLine = true, isError = !socksOk, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("If busy, a free port is picked automatically") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                Modifier.fillMaxWidth().clickable { remoteDns = !remoteDns }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Remote DNS (recommended)")
                    Text(
                        "The SSH server resolves host names. Turn off to use the DNS servers below over the tunnel.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = remoteDns, onCheckedChange = { remoteDns = it })
            }
            OutlinedTextField(
                dns, { dns = it }, label = { Text("DNS servers") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                enabled = !remoteDns,
                supportingText = { Text("Comma separated IPv4 addresses, queried over TCP through SSH") },
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}
