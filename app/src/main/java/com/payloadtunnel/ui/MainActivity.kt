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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.payloadtunnel.Graph
import com.payloadtunnel.core.AppLog
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import com.payloadtunnel.vpn.TunnelVpnService

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    PROFILES("Profiles", Icons.Filled.Storage),
    LOGS("Logs", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
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
        p.username.isBlank() -> "SSH username is empty"
        p.mode.usesProxy && p.proxy.isNotBlank() && Profile.parseHostPort(p.proxy) == null ->
            "Remote proxy must be host:port"
        else -> null
    }
}

@Composable
private fun MainScreen(onConnect: (Profile) -> Unit, onDisconnect: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var pickingApps by rememberSaveable { mutableStateOf(false) }

    val edit = editing
    if (edit != null) {
        BackHandler { editing = null }
        ProfileEditor(
            initial = edit,
            onSave = {
                Graph.profiles.save(it)
                editing = null
            },
            onCancel = { editing = null },
        )
        return
    }
    if (pickingApps) {
        BackHandler { pickingApps = false }
        AppPickerScreen(onDone = { pickingApps = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> HomeScreen(mod, onConnect, onDisconnect, onEditProfile = { editing = it })
            Tab.PROFILES -> ProfilesScreen(mod, onEdit = { editing = it })
            Tab.LOGS -> LogScreen(mod)
            Tab.SETTINGS -> SettingsScreen(mod, onPickApps = { pickingApps = true })
        }
    }
}
