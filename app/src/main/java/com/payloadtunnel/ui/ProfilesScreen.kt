package com.payloadtunnel.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.Graph
import com.payloadtunnel.core.AppLog
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfilesScreen(onBack: () -> Unit, onEdit: (Profile) -> Unit) {
    val context = LocalContext.current
    val profiles by Graph.profiles.profiles.collectAsState()
    val selectedId by Graph.profiles.selectedId.collectAsState()
    val tunnel by TunnelStatus.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    var exportIds by remember { mutableStateOf<Set<String>?>(null) }
    var topMenu by remember { mutableStateOf(false) }
    val locked = tunnel.status.isActive

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
            context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(Graph.profiles.exportJson(ids)) }
            Toast.makeText(context, "Profiles exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLog.e("Export failed: ${e.message}")
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val selected = profiles.firstOrNull { it.id == selectedId }
    val q = query.trim().lowercase()
    val filtered = profiles.filter {
        q.isEmpty() || it.name.lowercase().contains(q) || it.group.lowercase().contains(q) ||
            it.sshHost.lowercase().contains(q) || it.proxy.lowercase().contains(q)
    }
    val entries = Graph.profiles.entries(filtered)

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "Servers",
            left = { CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) },
            right = {
                CircleIconButton(Icons.Filled.Add, "Add server", { onEdit(Profile(name = "New server", sshPort = 22)) })
                Box {
                    CircleIconButton(Icons.Outlined.MoreVert, "More", { topMenu = true })
                    DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import JSON") }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = {
                                topMenu = false
                                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export all") }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                            onClick = {
                                topMenu = false
                                exportIds = null
                                exportLauncher.launch("payloadtunnel-profiles.json")
                            },
                        )
                    }
                }
            },
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            item {
                // Search
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, CardBorder, CircleShape).padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search by name or host", color = TextSecondary, fontSize = 14.sp)
                        BasicTextField(
                            query, { query = it }, singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(Lime), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (selected != null && q.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), highlighted = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatar(selected.group.ifBlank { selected.name }, 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (tunnel.status == Status.CONNECTED) "Active" else "Selected",
                                        color = TextSecondary, fontSize = 12.sp,
                                    )
                                    if (tunnel.status == Status.CONNECTED && tunnel.pingMs >= 0) {
                                        Text("  ${tunnel.pingMs}ms", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        selected.mode.label, color = TextPrimary, fontSize = 10.sp,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    selected.group.ifBlank { selected.name }, color = TextPrimary, fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (selected.group.isNotBlank()) {
                                    Text(selected.name, color = Lime, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                            Box(
                                Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Lime),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Check, null, tint = OnLime, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(Card)
                        .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                ) {
                    SectionLabel("Select servers")
                    if (entries.isEmpty()) {
                        Text(
                            if (profiles.isEmpty()) "No servers yet. Tap + to add one." else "No match",
                            color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
            items(entries, key = { it.first().group.ifBlank { it.first().id } }) { entry ->
                var menu by remember { mutableStateOf(false) }
                val isGroup = entry.first().group.isNotBlank()
                val isSel = entry.any { it.id == selectedId }
                val p = entry.firstOrNull { it.id == selectedId } ?: entry.first()
                Column(
                    Modifier.fillMaxWidth().background(Card).padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) CardHigh else Color.White.copy(alpha = 0.02f))
                            .border(1.dp, if (isSel) Lime.copy(alpha = 0.25f) else CardBorder, RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = { if (!locked) Graph.profiles.selectEntry(entry) },
                                onLongClick = { menu = true },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(if (isGroup) p.group else p.name, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isGroup) p.group else p.name, color = TextPrimary, fontSize = 15.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (isGroup) "${entry.size} configs · ${if (isSel) p.name else "choose after selecting"}"
                                else "${p.sshHost}:${p.sshPort}" + if (p.mode.usesProxy && p.proxy.isNotBlank()) " · ${p.proxy}" else "",
                                color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SignalBars(if (isSel && tunnel.status == Status.CONNECTED) 3 else 2)
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            if (isSel) Icons.Filled.Star else Icons.Outlined.StarOutline, null,
                            tint = if (isSel) Lime else TextSecondary, modifier = Modifier.size(20.dp),
                        )
                        Box {
                            Icon(
                                Icons.Outlined.MoreVert, "More", tint = TextSecondary,
                                modifier = Modifier.padding(start = 6.dp).size(20.dp).clip(CircleShape)
                                    .combinedClickable(onClick = { menu = true }),
                            )
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                val what = if (isGroup) " \"${p.name}\"" else ""
                                DropdownMenuItem(text = { Text("Edit$what") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit(p) })
                                DropdownMenuItem(text = { Text("Duplicate$what") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; Graph.profiles.duplicate(p.id) })
                                DropdownMenuItem(
                                    text = { Text(if (isGroup) "Export group" else "Export") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                                    onClick = {
                                        menu = false
                                        exportIds = entry.map { it.id }.toSet()
                                        exportLauncher.launch((if (isGroup) p.group else p.name).replace(Regex("[^A-Za-z0-9._-]+"), "_") + ".json")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete$what") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    enabled = !(locked && isSel), onClick = { menu = false; toDelete = p },
                                )
                            }
                        }
                    }
                    if (isGroup && isSel && entry.size > 1) {
                        VariantPicker(
                            variants = entry, selectedId = selectedId, enabled = !locked,
                            onSelect = { Graph.profiles.select(it.id) },
                            modifier = Modifier.padding(top = 6.dp, start = 24.dp),
                        )
                    }
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().height(14.dp)
                        .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)).background(Card),
                )
                Text(
                    "Tap to select · long-press or ⋮ to edit, duplicate, export or delete",
                    color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete server?") },
            text = { Text("\"${p.name}\" will be removed permanently.") },
            confirmButton = { TextButton(onClick = { Graph.profiles.delete(p.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
