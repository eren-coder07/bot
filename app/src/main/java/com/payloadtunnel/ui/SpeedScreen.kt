package com.payloadtunnel.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.core.TunnelStatus
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val scales = listOf(6f, 12f, 30f, 60f, 120f, 300f, 600f)

@Composable
fun SpeedScreen(traffic: Traffic, onBack: () -> Unit) {
    val state by TunnelStatus.state.collectAsState()
    var info by remember { mutableStateOf(false) }
    val mbps = traffic.downSpeed * 8 / 1_000_000f
    val peakMbps = traffic.peakDown * 8 / 1_000_000f
    val max = scales.firstOrNull { it >= peakMbps * 1.1f } ?: scales.last()
    val fraction by animateFloatAsState((mbps / max).coerceIn(0f, 1f), tween(800), label = "needle")

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "Speed Test",
            left = { CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) },
            right = { CircleIconButton(Icons.Outlined.Info, "About", { info = true }) },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoChip(Icons.Filled.SignalCellularAlt, "Ping - " + if (state.pingMs >= 0) "${state.pingMs}ms" else "--")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(state.profileName ?: "-", 16.dp)
                    Text(
                        "  " + (state.profileName ?: "Not connected"), color = TextPrimary, fontSize = 12.sp,
                        maxLines = 1, modifier = Modifier.padding(end = 4.dp),
                    )
                }
                InfoChip(Icons.Filled.DataUsage, "Total - " + formatBytes(traffic.up + traffic.down))
            }

            Spacer(Modifier.height(12.dp))
            Gauge(fraction, max, mbps)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeedCard(Icons.Filled.Download, formatSpeed(traffic.downSpeed), "Download", Modifier.weight(1f))
                SpeedCard(Icons.Filled.Upload, formatSpeed(traffic.upSpeed), "Upload", Modifier.weight(1f))
                SpeedCard(Icons.Filled.SignalCellularAlt, formatSpeed(traffic.peakDown), "Peak", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Last 40 seconds", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Sparkline(traffic.history, Modifier.fillMaxWidth().height(70.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            confirmButton = { TextButton(onClick = { info = false }) { Text("OK") } },
            title = { Text("Live speed") },
            text = {
                Text(
                    "The gauge shows the live download speed of all traffic in the tunnel. " +
                        "Ping is the time to open a connection through your SSH server.",
                )
            },
        )
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Signal, modifier = Modifier.size(14.dp))
        Text(" $text", color = TextPrimary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Gauge(fraction: Float, max: Float, mbps: Float) {
    Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val start = 135f
            val sweep = 270f
            val stroke = 16.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val c = Offset(size.width / 2, size.height / 2)
            val tl = Offset(c.x - r, c.y - r)
            val arcSize = Size(r * 2, r * 2)
            // track
            drawArc(Color.White.copy(alpha = 0.06f), start, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // progress
            drawArc(
                Brush.sweepGradient(listOf(LimeDeep, LimeDim, Lime, LimeDeep), center = c),
                start, sweep * fraction.coerceAtLeast(0.002f), false, tl, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // inner thin ring
            val r2 = r - 30.dp.toPx()
            drawArc(
                Signal.copy(alpha = 0.35f), start, sweep, false,
                Offset(c.x - r2, c.y - r2), Size(r2 * 2, r2 * 2), style = Stroke(1.5f),
            )
            // labels
            val paint = android.graphics.Paint().apply {
                color = TextPrimary.copy(alpha = 0.8f).toArgb()
                textSize = 12.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val rl = r2 - 18.dp.toPx()
            for (i in 0..6) {
                val a = Math.toRadians((start + sweep * i / 6f).toDouble())
                val v = max * i / 6f
                val label = if (v >= 10 || v == 0f) v.toInt().toString() else String.format(Locale.US, "%.0f", v)
                drawContext.canvas.nativeCanvas.drawText(
                    label, c.x + rl * cos(a).toFloat(), c.y + rl * sin(a).toFloat() + paint.textSize / 3, paint,
                )
            }
            // needle
            val a = Math.toRadians((start + sweep * fraction).toDouble())
            val tip = Offset(c.x + (r2 - 8.dp.toPx()) * cos(a).toFloat(), c.y + (r2 - 8.dp.toPx()) * sin(a).toFloat())
            val perp = a + Math.PI / 2
            val w = 9.dp.toPx()
            val b1 = Offset(c.x + w * cos(perp).toFloat(), c.y + w * sin(perp).toFloat())
            val b2 = Offset(c.x - w * cos(perp).toFloat(), c.y - w * sin(perp).toFloat())
            val path = Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }
            drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), start = tip, end = c))
        }
        Column(Modifier.padding(top = 200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(Locale.US, "%.2f", mbps), color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            Text("Mbps", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SpeedCard(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    GlassCard(modifier.height(104.dp), padding = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(15.dp)) }
        }
        Spacer(Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun Sparkline(values: List<Long>, modifier: Modifier) {
    Canvas(modifier) {
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / max) * size.height * 0.9f
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Lime.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(line, Lime, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
