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
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.payloadtunnel.Graph
import com.payloadtunnel.core.AppLog
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import com.payloadtunnel.data.Session
import com.payloadtunnel.vpn.TunnelVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class Tab(val label: String, val icon: ImageVector) {
    VPN("VPN", Icons.Outlined.Adjust),
    SPEED("Speed Test", Icons.Outlined.Speed),
    LOGS("Logs", Icons.AutoMirrored.Outlined.ReceiptLong),
}

class MainActivity : ComponentActivity() {

    private var pendingProfileId: String? = null
    private lateinit var adManager: AdManager

    /** Shows the "out of time — watch an ad?" dialog. */
    private val adPrompt = MutableStateFlow(false)

    /** True while a rewarded ad is loading/showing (covers the whole screen). */
    private val adLoading = MutableStateFlow(false)

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
        adManager = AdManager(this)
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
                    onWatchAd = ::watchAd,
                    adPrompt = adPrompt,
                    adLoading = adLoading,
                )
            }
        }
    }

    /** Starts the tunnel — only with time left on the key, otherwise prompts the ad. */
    private fun connect(profile: Profile) {
        if (Graph.session.timeRemaining.value <= 0) {
            adPrompt.value = true
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

    /** Shows a rewarded video; only when the video completes does the server credit the time. */
    private fun watchAd(onLoading: (Boolean) -> Unit) {
        val key = Graph.session.key.value
        if (key.isNullOrBlank()) {
            onLoading(false)
            Toast.makeText(this, "Sign in first", Toast.LENGTH_SHORT).show()
            return
        }
        adManager.showRewarded(
            onLoading = onLoading,
            onReward = {
                lifecycleScope.launch {
                    try {
                        val remaining = Graph.api.adReward(key, UUID.randomUUID().toString())
                        Graph.session.setTime(remaining)
                        Toast.makeText(this@MainActivity, "+${remaining / 60} min added", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        AppLog.w("ad reward failed: ${e.message}")
                        Toast.makeText(this@MainActivity, "Couldn't add the time. Try again.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() },
        )
    }
}

@Composable
private fun MainScreen(
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    onWatchAd: ((Boolean) -> Unit) -> Unit,
    adPrompt: MutableStateFlow<Boolean>,
    adLoading: MutableStateFlow<Boolean>,
) {
    val sessionState by Graph.session.state.collectAsState()
    val timeRemaining by Graph.session.timeRemaining.collectAsState()
    val configs by Graph.configs.configs.collectAsState()
    val prompt by adPrompt.collectAsState()
    val loading by adLoading.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.VPN) }
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    val traffic = rememberTraffic()

    // Validate the key and refresh configs each time the app starts.
    LaunchedEffect(Unit) { Graph.refresh() }

    // The gate shows until the key is valid. A cached session (time or configs) keeps the
    // app usable while the control server is unreachable.
    val showGate = when (sessionState) {
        Session.State.NO_KEY, Session.State.INVALID, Session.State.BANNED -> true
        Session.State.CHECKING, Session.State.OFFLINE -> timeRemaining <= 0 && configs.isEmpty()
        Session.State.VALID -> false
    }

    AppBackground {
        if (showGate) {
            KeyGateScreen()
        } else {
            if (tab != Tab.VPN) BackHandler { tab = Tab.VPN }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.VPN -> HomeScreen(
                            traffic = traffic,
                            onConnect = onConnect,
                            onDisconnect = onDisconnect,
                            onOpenAccount = { accountOpen = true },
                            onOpenSpeed = { tab = Tab.SPEED },
                            onWatchAd = { onWatchAd { adLoading.value = it } },
                        )
                        Tab.SPEED -> SpeedScreen(traffic, onBack = { tab = Tab.VPN })
                        Tab.LOGS -> LogScreen()
                    }
                }
                BottomBar(tab) { tab = it }
            }

            if (accountOpen) AccountSheet(
                onDismiss = { accountOpen = false },
                onWatchAd = { onWatchAd { adLoading.value = it } },
            )

            if (prompt) {
                AlertDialog(
                    onDismissRequest = { adPrompt.value = false },
                    title = { Text("You're out of time") },
                    text = {
                        Text("Watch a short ad to get +${Graph.session.adRewardSeconds / 60} more minutes of VPN.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            adPrompt.value = false
                            onWatchAd { adLoading.value = it }
                        }) { Text("Watch ad", color = Lime) }
                    },
                    dismissButton = {
                        TextButton(onClick = { adPrompt.value = false }) { Text("Not now") }
                    },
                )
            }
        }

        if (loading) AdLoadingOverlay()
    }
}

@Composable
private fun AdLoadingOverlay() {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(padding = 22.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Lime, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(14.dp))
                Text("Loading ad…", color = TextPrimary, fontSize = 14.sp)
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
