package com.payloadtunnel.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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
import com.payloadtunnel.net.ApiException
import com.payloadtunnel.security.SecurityGuard
import com.payloadtunnel.vpn.TunnelVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        ads = AdManager(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { AppTheme { Root() } }
    }

    @Composable
    private fun Root() {
        val scope = rememberCoroutineScope()
        val sessionState by Graph.session.state.collectAsState()
        val configs by Graph.configs.configs.collectAsState()
        var security by remember { mutableStateOf(SecurityGuard.check(this).block()) }
        var adLoading by remember { mutableStateOf(false) }
        setAdLoading = { adLoading = it }

        // Splash screen state
        var showSplash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(2200)
            showSplash = false
        }

        LaunchedEffect(Unit) {
            if (Graph.session.hasKey()) Graph.refresh()
            security = SecurityGuard.check(this@MainActivity).block()
        }

        // Show interstitial after splash on cold start
        var interstitialShown by remember { mutableStateOf(false) }
        LaunchedEffect(showSplash) {
            if (!showSplash && !interstitialShown) {
                interstitialShown = true
                delay(500)
                ads.showInterstitial()
            }
        }

        if (showSplash) {
            SplashScreen()
            return
        }

        AnimatedVisibility(
            visible = !showSplash,
            enter = fadeIn(tween(500)),
        ) {
            if (security != null) {
                SecurityBlockScreen(security!!) { security = SecurityGuard.check(this@MainActivity).block() }
                return@AnimatedVisibility
            }

            val gate = !Graph.session.hasKey() || sessionState == Session.State.INVALID || sessionState == Session.State.BANNED
            when {
                gate -> KeyGateScreen(sessionState) { key -> scope.launch { Graph.signIn(key) } }
                configs.isEmpty() && sessionState == Session.State.CHECKING -> LoadingScreen()
                configs.isEmpty() -> KeyGateScreen(Session.State.OFFLINE) { key -> scope.launch { Graph.signIn(key) } }
                else -> MainScaffold(
                    onConnect = ::connect,
                    onDisconnect = { TunnelVpnService.disconnect(this@MainActivity) },
                    onRefresh = { done -> scope.launch { done(Graph.refresh()) } },
                    onWatchAd = { watchAd() },
                    adLoading = adLoading,
                )
            }
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

@Composable
private fun SplashScreen() {
    val logoScale = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val ringRotation = rememberInfiniteTransition(label = "splash")
    val rotation by ringRotation.animateFloat(
        0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "rot",
    )

    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, tween(800))
    }
    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(600))
        delay(200)
        textAlpha.animateTo(1f, tween(500))
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(BgTop, Color(0xFF10150B), BgBottom))
        ).background(
            Brush.radialGradient(
                listOf(Lime.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(540f, 800f), radius = 1000f,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        // Animated ring behind the logo
        Canvas(Modifier.size(220.dp).graphicsLayer { alpha = logoAlpha.value }) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            for (i in 0 until 60) {
                val a = (i * 2 * PI / 60 + Math.toRadians(rotation.toDouble())).toFloat()
                val wobble = ((sin(i * 0.8) + 1) / 2).toFloat()
                val r1 = r * 0.65f
                val r2 = r * (0.80f + 0.15f * wobble)
                val alpha = (0.1f + 0.4f * wobble)
                drawLine(
                    Lime.copy(alpha = alpha),
                    Offset(c.x + r1 * cos(a), c.y + r1 * sin(a)),
                    Offset(c.x + r2 * cos(a), c.y + r2 * sin(a)),
                    strokeWidth = 2f, cap = StrokeCap.Round,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(100.dp)
                    .graphicsLayer { scaleX = logoScale.value; scaleY = logoScale.value; alpha = logoAlpha.value }
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Lime, LimeDim)))
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Bolt, null, tint = OnLime, modifier = Modifier.size(50.dp)) }
            Spacer(Modifier.height(20.dp))
            Text(
                "Jat Vpn",
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Poppins,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "just another tunnel vpn",
                color = TextSecondary,
                fontSize = 14.sp,
                fontFamily = Poppins,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
        }
    }
}
