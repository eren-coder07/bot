package com.payloadtunnel.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.payloadtunnel.Graph
import com.payloadtunnel.core.AppLog
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import com.payloadtunnel.data.Session
import com.payloadtunnel.net.ApiException
import com.payloadtunnel.security.SecurityGuard
import com.payloadtunnel.vpn.TunnelVpnService
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var pendingProfileId: String? = null
    private lateinit var ads: AdManager
    private var setAdLoading: (Boolean) -> Unit = {}

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingProfileId
        pendingProfileId = null
        if (r.resultCode == Activity.RESULT_OK && id != null) TunnelVpnService.connect(this, id)
        else {
            AppLog.fail("VPN permission is required to connect.")
            TunnelStatus.set(Status.ERROR, "VPN permission denied")
        }
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (compromised()) { killApp(); return }
        enableEdgeToEdge()
        ads = AdManager(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { AppTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        if (compromised()) killApp()
    }

    private fun compromised(): Boolean = SecurityGuard.check(this).block() != null

    private fun killApp() {
        runCatching { finishAffinity() }
        android.os.Process.killProcess(android.os.Process.myPid())
        kotlin.system.exitProcess(10)
    }

    @Composable
    private fun Root() {
        val scope = rememberCoroutineScope()
        val sessionState by Graph.session.state.collectAsState()
        val configs by Graph.configs.configs.collectAsState()
        var adLoading by remember { mutableStateOf(false) }
        setAdLoading = { adLoading = it }

        LaunchedEffect(Unit) {
            if (Graph.session.hasKey()) Graph.refresh()
        }

        val gate = !Graph.session.hasKey() || sessionState == Session.State.INVALID || sessionState == Session.State.BANNED
        when {
            gate -> KeyGateScreen(sessionState) { key -> scope.launch { Graph.signIn(key) } }
            configs.isEmpty() && sessionState == Session.State.CHECKING -> LoadingScreen()
            configs.isEmpty() -> KeyGateScreen(Session.State.OFFLINE) { key -> scope.launch { Graph.signIn(key) } }
            else -> MainScaffold(
                onConnect = ::connect,
                onDisconnect = { TunnelVpnService.disconnect(this) },
                onRefresh = { done -> scope.launch { done(Graph.refresh()) } },
                onWatchAd = { watchAd() },
                adLoading = adLoading,
            )
        }
    }

    private fun connect(profile: Profile) {
        if (Graph.session.timeRemaining.value <= 0) {
            Toast.makeText(this, "No time left — watch an ad to get more.", Toast.LENGTH_LONG).show()
            watchAd()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingProfileId = profile.id
            runCatching { vpnPermission.launch(intent) }
        } else {
            TunnelVpnService.connect(this, profile.id)
        }
    }

    private fun watchAd() {
        ads.showRewarded(
            onLoading = { setAdLoading(it) },
            onReward = {
                val key = Graph.session.key.value ?: return@showRewarded
                lifecycleScope.launch {
                    try {
                        Graph.session.setTime(Graph.api.adReward(key, UUID.randomUUID().toString()))
                        Toast.makeText(this@MainActivity, "Time added!", Toast.LENGTH_SHORT).show()
                    } catch (e: ApiException) {
                        Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onError = { msg -> setAdLoading(false); Toast.makeText(this, msg, Toast.LENGTH_LONG).show() },
        )
    }
}
