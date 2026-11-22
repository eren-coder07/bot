package com.payloadtunnel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.core.AppLog

@Composable
fun LogScreen() {
    val context = LocalContext.current
    val entries by AppLog.entries.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "Logs",
            right = {
                CircleIconButton(Icons.Outlined.ContentCopy, "Copy log", {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Payload Tunnel log", AppLog.asText()))
                    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                })
                CircleIconButton(Icons.Outlined.DeleteSweep, "Clear log", { AppLog.clear() })
            },
        )
        GlassCard(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), padding = 0.dp) {
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No log entries yet", color = TextSecondary)
                }
                return@GlassCard
            }
            SelectionContainer {
                LazyColumn(state = listState, contentPadding = PaddingValues(12.dp)) {
                    items(entries, key = { it.seq }) { e ->
                        val color = when (e.level) {
                            AppLog.Level.ERROR -> Failed
                            AppLog.Level.WARN -> Working
                            AppLog.Level.OK -> Lime
                            AppLog.Level.INFO -> TextPrimary
                        }
                        Text(
                            e.format(), color = color, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp,
                            lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
