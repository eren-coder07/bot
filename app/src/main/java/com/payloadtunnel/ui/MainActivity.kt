package com.payloadtunnel.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.payloadtunnel.Graph
import com.payloadtunnel.core.AppLog
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import com.payloadtunnel.vpn.TunnelVpnService

enum class Tab(val label: String, val icon: ImageVector) {
    VPN("VPN", Icons.Outlined.Adjust),
    SERVERS("Servers", Icons.Outlined.Public),
    SPEED("Speed Test", Icons.Outlined.Speed),
    LOGS("Logs", Icons.AutoMirrored.Outlined.ReceiptLong),
}

class MainActivity : ComponentActivity() {

    private var pendingProfileId: String? = null

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingProfileId
        pendingProfileId = null
        if (result.resultCode == Activity.RESULT_OK && id != null) {
            TunnelVpnService.connect(this, id)
        } else {
            AppLog.e("VPN permission denied. The app cannot create the tunnel without it.")
            TunnelStatus.set(Status.ERROR, "VPN permission denied")
            Toast.makeText(this, "VPN permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) AppLog.w("Notification permission denied: the status notification will be hidden")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppTheme {
                MainScreen(
                    onConnect = ::connect,
                    onDisconnect = { TunnelVpnService.disconnect(this) },
                )
            }
        }
    }

    private fun connect(profile: Profile) {
        val problems = validate(profile)
        if (problems != null) {
            AppLog.e(problems)
            TunnelStatus.set(Status.ERROR, problems)
            Toast.makeText(this, problems, Toast.LENGTH_LONG).show()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingProfileId = profile.id
            try {
                vpnPermission.launch(intent)
            } catch (e: Exception) {
                AppLog.e("Cannot show the VPN permission dialog: ${e.message}")
            }
        } else {
            TunnelVpnService.connect(this, profile.id)
        }
    }

    private fun validate(p: Profile): String? = when {
        p.sshHost.isBlank() -> "SSH host is empty"
        p.sshPort !in 1..65535 -> "SSH port is invalid"
        p.mode.isSsh && p.username.isBlank() -> "SSH username is empty"
        p.mode.isVless && runCatching { java.util.UUID.fromString(p.vlessUuid.trim()) }.isFailure -> "VLESS UUID is invalid"
        p.mode.usesProxy && p.proxy.isNotBlank() && Profile.parseHostPort(p.proxy) == null ->
            "Remote proxy must be host:port"
        else -> null
    }
}

@Composable
private fun MainScreen(onConnect: (Profile) -> Unit, onDisconnect: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.VPN) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var pickingApps by rememberSaveable { mutableStateOf(false) }
    val traffic = rememberTraffic()

    AppBackground {
        val edit = editing
        when {
            edit != null -> {
                BackHandler { editing = null }
                ProfileEditor(
                    initial = edit,
                    onSave = {
                        Graph.profiles.save(it)
                        editing = null
                    },
                    onCancel = { editing = null },
                )
            }
            pickingApps -> {
                BackHandler { pickingApps = false }
                AppPickerScreen(onDone = { pickingApps = false })
            }
            settingsOpen -> {
                BackHandler { settingsOpen = false }
                SettingsScreen(onBack = { settingsOpen = false }, onPickApps = { pickingApps = true })
            }
            else -> {
                if (tab != Tab.VPN) BackHandler { tab = Tab.VPN }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.VPN -> HomeScreen(
                                traffic = traffic,
                                onConnect = onConnect,
                                onDisconnect = onDisconnect,
                                onOpenMenu = { settingsOpen = true },
                                onOpenSpeed = { tab = Tab.SPEED },
                                onOpenServers = { tab = Tab.SERVERS },
                            )
                            Tab.SERVERS -> ProfilesScreen(onBack = { tab = Tab.VPN }, onEdit = { editing = it })
                            Tab.SPEED -> SpeedScreen(traffic, onBack = { tab = Tab.VPN })
                            Tab.LOGS -> LogScreen()
                        }
                    }
                    BottomBar(tab) { tab = it }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF12170D), Color(0xFF0B0E08)))),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { t ->
                val active = t == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(t) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(if (active) Lime.copy(alpha = 0.14f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(t.icon, t.label, tint = if (active) Lime else TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, color = if (active) Lime else TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
