package com.payloadtunnel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.data.Profile

/** Dropdown to choose which config of a server group ("Free Vip") is used. */
@Composable
fun VariantPicker(
    variants: List<Profile>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val current = variants.firstOrNull { it.id == selectedId } ?: variants.first()
    Box(modifier) {
        Surface(
            onClick = { if (enabled) open = true },
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.04f),
            border = BorderStroke(1.dp, Lime.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, tint = Lime, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${current.group} · config", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        current.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(current.mode.label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (enabled) TextPrimary else TextSecondary)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            variants.forEach { v ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(v.name, fontWeight = if (v.id == current.id) FontWeight.SemiBold else FontWeight.Normal)
                            Text(v.mode.label + " · " + v.sshHost, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                        }
                    },
                    trailingIcon = { if (v.id == current.id) Icon(Icons.Filled.Check, null, tint = Lime) },
                    onClick = {
                        open = false
                        onSelect(v)
                    },
                )
            }
        }
    }
}
