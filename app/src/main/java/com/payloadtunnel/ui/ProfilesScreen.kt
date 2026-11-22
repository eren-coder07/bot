package com.payloadtunnel.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.payloadtunnel.Graph
import com.payloadtunnel.core.AppLog
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(modifier: Modifier, onEdit: (Profile) -> Unit) {
    val context = LocalContext.current
    val profiles by Graph.profiles.profiles.collectAsState()
    val selectedId by Graph.profiles.selectedId.collectAsState()
    val tunnel by TunnelStatus.state.collectAsState()
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    var exportIds by remember { mutableStateOf<Set<String>?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val n = Graph.profiles.importJson(text)
            AppLog.i("Imported $n profile(s)")
            Toast.makeText(context, "Imported $n profile(s)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLog.e("Import failed: ${e.message}")
            Toast.makeText(context, "Import failed: not a valid profile JSON", Toast.LENGTH_LONG).show()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val ids = exportIds
        exportIds = null
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                it.write(Graph.profiles.exportJson(ids))
            }
            Toast.makeText(context, "Profiles exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLog.e("Export failed: ${e.message}")
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Import JSON")
                    }
                    IconButton(onClick = {
                        exportIds = null
                        exportLauncher.launch("payloadtunnel-profiles.json")
                    }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "Export all as JSON")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(Profile(name = "New profile", sshPort = 22)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add profile")
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No profiles yet. Tap + to add one.")
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
        ) {
            items(profiles, key = { it.id }) { p ->
                val locked = tunnel.status.isActive
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable(enabled = !locked) { Graph.profiles.select(p.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = p.id == selectedId, onClick = { Graph.profiles.select(p.id) }, enabled = !locked)
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${p.mode.label} · ${p.sshHost}:${p.sshPort}",
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (p.mode.usesProxy && p.proxy.isNotBlank()) {
                                Text(
                                    "Proxy ${p.proxy}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                )
                            }
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = { menu = false; onEdit(p) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                    onClick = { menu = false; Graph.profiles.duplicate(p.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                                    onClick = {
                                        menu = false
                                        exportIds = setOf(p.id)
                                        exportLauncher.launch(p.name.replace(Regex("[^A-Za-z0-9._-]+"), "_") + ".json")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    enabled = !(locked && p.id == selectedId),
                                    onClick = { menu = false; toDelete = p },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete profile?") },
            text = { Text("\"${p.name}\" will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = { Graph.profiles.delete(p.id); toDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
