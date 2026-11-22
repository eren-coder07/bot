package com.payloadtunnel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.payloadtunnel.data.Profile
import com.payloadtunnel.data.TunnelMode

/** Short human description of how a config connects, e.g. "SSH · HTTP proxy". */
fun TunnelMode.shortDescription(): String = when (this) {
    TunnelMode.SSH_DIRECT -> "SSH · direct"
    TunnelMode.SSH_PAYLOAD -> "SSH · payload"
    TunnelMode.SSH_PROXY_PAYLOAD -> "SSH · HTTP proxy"
    TunnelMode.SSH_PROXY -> "SSH · HTTP CONNECT"
    TunnelMode.SSH_TLS -> "SSH · SSL/SNI"
    TunnelMode.SSH_TLS_PAYLOAD -> "SSH · SSL + payload"
    TunnelMode.VLESS_WS_TLS -> "VLESS · WS/TLS"
}

/** A tappable field showing the current choice, like a dropdown. */
@Composable
fun SelectorField(
    label: String,
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.size(6.dp))
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.04f),
            border = BorderStroke(1.dp, if (enabled) Lime.copy(alpha = 0.3f) else CardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                leading()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (enabled) Lime else TextSecondary)
            }
        }
    }
}

/** Square lime "globe" icon used for configs (like the reference app). */
@Composable
fun ConfigIcon(size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(Lime.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Language, null, tint = Lime, modifier = Modifier.size((size * 0.6).dp)) }
}

data class PickerItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val leading: @Composable () -> Unit,
)

/**
 * Full-height selection dialog: optional search, optional two tabs, radio list.
 * [tabs] = (label, items) pairs; with one tab no tab bar is shown.
 */
@Composable
fun PickerDialog(
    title: String,
    tabs: List<Pair<String, List<PickerItem>>>,
    selectedId: String?,
    searchable: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PickerContent(title, tabs, selectedId, searchable, onPick)
    }
}

@Composable
fun PickerContent(
    title: String,
    tabs: List<Pair<String, List<PickerItem>>>,
    selectedId: String?,
    searchable: Boolean,
    onPick: (String) -> Unit,
    initialTab: Int = 0,
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(initialTab) }
    run {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 40.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF12170E))
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                .padding(16.dp),
        ) {
            Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            if (searchable) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search config name", color = TextSecondary, fontSize = 15.sp)
                        BasicTextField(
                            query, { query = it }, singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                            cursorBrush = SolidColor(Lime), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
            }
            if (tabs.size > 1) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEachIndexed { i, (label, _) ->
                        val active = i == tab
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (active) Lime else Color.Transparent)
                                .clickable { tab = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label.uppercase(), color = if (active) OnLime else TextSecondary,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(10.dp))
            }
            val q = query.trim().lowercase()
            val items = tabs.getOrNull(tab)?.second.orEmpty()
                .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q) }
            if (items.isEmpty()) {
                Text("Nothing found", color = TextSecondary, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    val sel = item.id == selectedId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Lime.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f))
                            .border(1.dp, if (sel) Lime.copy(alpha = 0.45f) else CardBorder, RoundedCornerShape(16.dp))
                            .clickable { onPick(item.id) }
                            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.leading()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        RadioButton(
                            selected = sel, onClick = { onPick(item.id) },
                            colors = RadioButtonDefaults.colors(selectedColor = Lime, unselectedColor = TextSecondary),
                        )
                    }
                }
            }
        }
    }
}

/** Server + config selector pair used on Home and Servers. */
@Composable
fun ServerAndConfigSelectors(
    profiles: List<Profile>,
    entries: List<List<Profile>>,
    selected: Profile?,
    enabled: Boolean,
    onSelectEntry: (List<Profile>) -> Unit,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    hideDetails: Boolean = false,
    serverTrailing: (@Composable () -> Unit)? = null,
) {
    fun detail(p: Profile) = if (hideDetails) "" else p.mode.shortDescription() + " · " + p.sshHost
    var serverOpen by remember { mutableStateOf(false) }
    var configOpen by remember { mutableStateOf(false) }
    val entry = entries.firstOrNull { e -> e.any { it.id == selected?.id } }
    val entryName = selected?.group?.ifBlank { null } ?: selected?.name ?: "No server"

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectorField(
            label = "Server",
            title = entryName,
            subtitle = if ((entry?.size ?: 0) > 1) "${entry!!.size} configs" else if (hideDetails) "" else selected?.mode?.shortDescription() ?: "Tap to add one",
            leading = { ProfileAvatar(entryName, 38.dp) },
            enabled = enabled,
            onClick = { serverOpen = true },
            trailing = serverTrailing,
        )
        if (selected != null) {
            SelectorField(
                label = "Config",
                title = selected.name,
                subtitle = detail(selected),
                leading = { ConfigIcon(38) },
                enabled = enabled,
                onClick = { configOpen = true },
            )
        }
    }

    if (serverOpen) {
        PickerDialog(
            title = "Choose server",
            tabs = listOf(
                "Servers" to entries.map { e ->
                    val first = e.first()
                    val name = first.group.ifBlank { first.name }
                    PickerItem(
                        id = first.group.ifBlank { first.id },
                        title = name,
                        subtitle = if (e.size > 1) "${e.size} configs" else if (hideDetails) "" else first.mode.shortDescription(),
                        leading = { ProfileAvatar(name, 40.dp) },
                    )
                },
            ),
            selectedId = entry?.first()?.let { it.group.ifBlank { it.id } },
            searchable = entries.size > 6,
            onPick = { key ->
                entries.firstOrNull { it.first().group.ifBlank { it.first().id } == key }?.let(onSelectEntry)
                serverOpen = false
                // Go straight to the config list when the server has several configs.
                if ((entries.firstOrNull { it.first().group.ifBlank { it.first().id } == key }?.size ?: 0) > 1) configOpen = true
            },
            onDismiss = { serverOpen = false },
        )
    }
    if (configOpen && selected != null) {
        fun item(p: Profile) = PickerItem(
            id = p.id,
            title = if (p.group.isNotBlank() && entry?.contains(p) != true) "${p.group} · ${p.name}" else p.name,
            subtitle = detail(p),
            leading = { ConfigIcon(40) },
        )
        val recommended = (entry ?: listOf(selected)).map(::item)
        PickerDialog(
            title = entryName,
            tabs = if (profiles.size > recommended.size) {
                listOf("Recommended" to recommended, "All configs" to profiles.map(::item))
            } else {
                listOf("Configs" to recommended)
            },
            selectedId = selected.id,
            searchable = true,
            onPick = {
                onSelectProfile(it)
                configOpen = false
            },
            onDismiss = { configOpen = false },
        )
    }
}
