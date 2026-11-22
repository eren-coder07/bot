package com.payloadtunnel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.BuildConfig
import com.payloadtunnel.Graph
import com.payloadtunnel.data.ShareNet
import com.payloadtunnel.vpn.TunnelVpnService

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onWatchAd: () -> Unit,
    adLoading: Boolean,
    onRefresh: ((String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val time by Graph.session.timeRemaining.collectAsState()
    val keyVal by Graph.session.key.collectAsState()
    val share by ShareNet.state.collectAsState()
    var refreshing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Account", left = { CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Time
            GlassCard(Modifier.fillMaxWidth(), highlighted = true) {
                Text("Time remaining", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (time <= 0) "No time left" else formatDuration(time * 1000),
                    color = if (time <= 0) Failed else TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Watch ad  +${Graph.session.adRewardSeconds / 60} min",
                    loading = adLoading, onClick = onWatchAd,
                )
            }

            // Share net
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Wifi, null, tint = Lime, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Share connection", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Let a PC on the same hotspot/Wi-Fi use this tunnel", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = share.enabled, onCheckedChange = { ShareNet.setEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = OnLime, checkedTrackColor = Lime),
                    )
                }
                if (share.enabled) {
                    Spacer(Modifier.height(10.dp))
                    val addr = share.address ?: ShareNet.localAddress()
                    val port = if (share.port > 0) share.port else ShareNet.DEFAULT_PORT
                    if (share.active && addr != null) {
                        val proxy = "$addr:$port"
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lime.copy(alpha = 0.08f)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("On your PC set an HTTP or SOCKS5 proxy to:", color = TextSecondary, fontSize = 12.sp)
                                Text(proxy, color = Lime, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                            }
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color_white()).clickable {
                                    copy(context, proxy); toast(context, "Copied")
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.ContentCopy, "Copy", tint = Lime, modifier = Modifier.size(16.dp)) }
                        }
                    } else {
                        Text(
                            "Connect the VPN, then share starts automatically. Your PC must be on this phone's hotspot or the same Wi-Fi.",
                            color = TextSecondary, fontSize = 12.sp,
                        )
                    }
                }
            }

            // Refresh
            RowAction(Icons.Filled.Refresh, "Refresh servers", "Fetch the latest server list", loading = refreshing) {
                if (!refreshing) {
                    refreshing = true
                    onRefresh { err -> refreshing = false; toast(context, err ?: "Servers updated") }
                }
            }

            // Account key
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Access key", color = TextSecondary, fontSize = 12.sp)
                Text(mask(keyVal), color = TextPrimary, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(12.dp))
                RowAction(Icons.Filled.Login, "Use a different key", "Sign out and enter another key") {
                    TunnelVpnService.disconnect(context)
                    Graph.session.clearKey()
                }
            }

            Text(
                "Jat Vpn ${BuildConfig.VERSION_NAME} · just another tunnel vpn",
                color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RowAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, loading: Boolean = false, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(enabled = !loading, onClick = onClick), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Lime.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                if (loading) androidx.compose.material3.CircularProgressIndicator(color = Lime, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else Icon(icon, null, tint = Lime, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

private fun Color_white() = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f)
private fun mask(key: String?): String {
    if (key.isNullOrBlank()) return "—"
    if (key.length <= 4) return "••••"
    return key.takeLast(4).padStart(key.length, '•')
}
private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("proxy", text))
}
private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
