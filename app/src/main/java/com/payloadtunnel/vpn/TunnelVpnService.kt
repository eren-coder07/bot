package com.payloadtunnel.vpn

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.payloadtunnel.App
import com.payloadtunnel.Graph
import com.payloadtunnel.R
import com.payloadtunnel.core.AppLog
import com.payloadtunnel.core.HandshakeException
import com.payloadtunnel.core.Payload
import com.payloadtunnel.core.ProxyHandshake
import com.payloadtunnel.core.Socks5Server
import com.payloadtunnel.core.SshConnection
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelState
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.AppFilterMode
import com.payloadtunnel.data.Profile
import com.payloadtunnel.ui.MainActivity
import com.payloadtunnel.ui.formatBytes
import com.payloadtunnel.ui.formatSpeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.net.Socket

class TunnelVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var notifJob: Job? = null

    @Volatile private var ssh: SshConnection? = null
    @Volatile private var socks: Socks5Server? = null
    @Volatile private var pendingSocket: Socket? = null
    @Volatile private var stopRequested = false

    private var tun: ParcelFileDescriptor? = null
    private var hevRunning = false
    private var hevPort = 0
    private var profile: Profile? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                userStop()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_CONNECT, or started by the system for always-on VPN.
                val id = intent?.getStringExtra(EXTRA_PROFILE_ID)
                val p = Graph.profiles.get(id) ?: Graph.profiles.selected()
                if (p == null) {
                    AppLog.e("No profile selected")
                    TunnelStatus.set(Status.ERROR, "No profile selected")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundCompat(buildNotification("Connecting to ${p.name}…"))
                startTunnel(p)
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(p: Profile) {
        val previous = job
        stopRequested = false
        profile = p
        job = scope.launch {
            if (previous != null) {
                closeConnections()
                previous.cancel()
                previous.join()
            }
            runLoop(p)
        }
        if (notifJob == null) {
            notifJob = scope.launch {
                while (isActive) {
                    delay(2000)
                    updateNotification()
                }
            }
        }
    }

    private suspend fun runLoop(p: Profile) {
        val settings = Graph.settings.current
        var failures = 0
        var lastError: String? = null
        TunnelStatus.resetTraffic()
        TunnelStatus.update { TunnelState(status = Status.CONNECTING, profileName = p.name) }
        AppLog.i("──── Starting \"${p.name}\" (${p.mode.label}) ────")

        while (scope.isActive && !stopRequested) {
            try {
                connectOnce(p)
                failures = 0
                lastError = null
                var tick = 0
                while (!stopRequested && ssh?.isConnected == true) {
                    if (tick++ % 15 == 0) measurePing()
                    delay(1000)
                }
                if (stopRequested) break
                AppLog.w("SSH connection lost")
                lastError = "SSH connection lost"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (stopRequested) break
                lastError = when (e) {
                    is HandshakeException -> e.message
                    else -> e.message ?: e.javaClass.simpleName
                }
                AppLog.e(lastError ?: "Unknown error")
                TunnelStatus.set(Status.ERROR, lastError)
            }
            closeConnections()
            if (stopRequested) break

            if (!settings.autoReconnect) break
            failures++
            if (settings.maxRetries > 0 && failures > settings.maxRetries) {
                AppLog.e("Giving up after ${settings.maxRetries} reconnect attempt(s)")
                break
            }
            val wait = minOf(2_000L * failures, 10_000L)
            val limit = if (settings.maxRetries > 0) "/${settings.maxRetries}" else ""
            val msg = "Reconnecting in ${wait / 1000}s (attempt $failures$limit)"
            AppLog.w(msg)
            TunnelStatus.update { it.copy(status = Status.RECONNECTING, message = msg, connectedAt = 0) }
            updateNotification()
            var waited = 0L
            while (waited < wait && !stopRequested) {
                delay(250); waited += 250
            }
        }

        shutdown(if (stopRequested) null else lastError)
    }

    private fun connectOnce(p: Profile) {
        TunnelStatus.update { it.copy(status = Status.CONNECTING, message = null, profileName = p.name) }
        updateNotification()

        val prepared = ProxyHandshake(
            profile = p,
            userAgent = userAgent(),
            protect = { protect(it) },
            onSendingPayload = {
                TunnelStatus.set(Status.SENDING_PAYLOAD)
                updateNotification()
            },
            register = { pendingSocket = it },
        ).open()
        if (stopRequested) {
            runCatching { prepared.socket.close() }
            return
        }

        TunnelStatus.set(Status.AUTHENTICATING)
        updateNotification()
        AppLog.i("Starting SSH session, authenticating as \"${p.username}\"")
        val conn = SshConnection.connect(p, prepared)
        pendingSocket = null
        ssh = conn
        AppLog.ok("SSH authenticated (server: ${conn.serverVersion ?: "unknown"})")
        if (stopRequested) return

        val server = Socks5Server(conn)
        val port = server.start(p.localSocksPort)
        socks = server
        AppLog.ok("SOCKS5 proxy started on 127.0.0.1:$port (dynamic forward over SSH)")

        if (tun == null) establishVpn(p)
        if (!hevRunning || hevPort != port) startTun2Socks(port, p.remoteDns)

        TunnelStatus.update {
            it.copy(
                status = Status.CONNECTED, message = null, connectedAt = System.currentTimeMillis(),
                socksPort = port, serverVersion = conn.serverVersion, pingMs = -1,
            )
        }
        AppLog.ok("Connected ✔")
        updateNotification()
    }

    /** Latency through the tunnel: time to open (and close) a channel to a public DNS server. */
    private fun measurePing() {
        val conn = ssh ?: return
        scope.launch {
            val t0 = System.nanoTime()
            val ms = try {
                conn.openDirect("1.1.1.1", 53, 5_000).close()
                (System.nanoTime() - t0) / 1_000_000
            } catch (e: Exception) {
                -1L
            }
            TunnelStatus.update { it.copy(pingMs = ms) }
        }
    }

    private fun establishVpn(p: Profile) {
        val settings = Graph.settings.current
        val builder = Builder()
            .setSession(p.name.ifBlank { getString(R.string.app_name) })
            .setMtu(MTU)
            .addAddress(TUN_IPV4, 32)
            .addRoute("0.0.0.0", 0)
        // No IPv6 address/route: Android then blocks IPv6 for tunnelled apps, so they fail
        // fast and use IPv4 instead of hanging on IPv6 the SSH server usually cannot reach.

        val usedDns = mutableListOf<String>()
        if (p.remoteDns) {
            builder.addDnsServer(MAPDNS_IP)
            usedDns += "remote (resolved by SSH server)"
        } else {
            for (d in p.dnsServers) {
                try {
                    if (!d.matches(Regex("[0-9.]+"))) throw IllegalArgumentException("not an IPv4 address")
                    builder.addDnsServer(InetAddress.getByName(d))
                    usedDns += d
                } catch (e: Exception) {
                    AppLog.w("Ignoring invalid DNS server \"$d\" (must be an IPv4 address)")
                }
            }
            if (usedDns.isEmpty()) {
                builder.addDnsServer("1.1.1.1")
                usedDns += "1.1.1.1"
            }
            usedDns[usedDns.size - 1] = usedDns.last() + " (over TCP through SSH)"
        }

        val appsDesc = applyAppFilter(builder, settings.appFilterMode, settings.selectedApps)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.setConfigureIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

        tun = builder.establish()
            ?: throw IllegalStateException("VPN permission was not granted (or another VPN app took over)")
        AppLog.ok("VPN interface up · DNS ${usedDns.joinToString()} · $appsDesc")
    }

    private fun applyAppFilter(builder: Builder, mode: AppFilterMode, apps: Set<String>): String {
        val self = packageName
        fun installed(pkg: String) = try {
            packageManager.getApplicationInfo(pkg, 0); true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        when (mode) {
            AppFilterMode.INCLUDE -> {
                val list = apps.filter { it != self && installed(it) }
                if (list.isNotEmpty()) {
                    list.forEach { builder.addAllowedApplication(it) }
                    return "${list.size} selected app(s) only"
                }
                AppLog.w("Per-app VPN: no apps selected, tunnelling all apps")
            }
            AppFilterMode.EXCLUDE -> {
                builder.addDisallowedApplication(self)
                val list = apps.filter { it != self && installed(it) }
                list.forEach { builder.addDisallowedApplication(it) }
                return "all apps except ${list.size} excluded"
            }
            AppFilterMode.ALL -> Unit
        }
        // Our own SSH traffic must never enter the tunnel.
        builder.addDisallowedApplication(self)
        return "all apps"
    }

    private fun startTun2Socks(port: Int, remoteDns: Boolean) {
        val fd = tun ?: return
        if (hevRunning) {
            TProxyNative.TProxyStopService()
            hevRunning = false
        }
        val config = File(cacheDir, "tun2socks.yml")
        config.writeText(tun2socksConfig(port, remoteDns))
        if (!TProxyNative.TProxyStartService(config.absolutePath, fd.fd)) {
            throw IllegalStateException("tun2socks failed to start")
        }
        hevRunning = true
        hevPort = port
        AppLog.ok("tun2socks running: all TUN traffic → SOCKS5 127.0.0.1:$port")
    }

    /** Closes SSH + SOCKS but keeps the VPN interface so nothing leaks while reconnecting. */
    private fun closeConnections() {
        pendingSocket?.let { runCatching { it.close() } }
        pendingSocket = null
        socks?.stop()
        socks = null
        ssh?.close()
        ssh = null
    }

    private fun shutdown(error: String?) {
        closeConnections()
        if (hevRunning) {
            runCatching { TProxyNative.TProxyStopService() }
            hevRunning = false
        }
        tun?.let { runCatching { it.close() } }
        tun = null
        notifJob?.cancel()
        notifJob = null
        if (error != null) {
            TunnelStatus.update { it.copy(status = Status.ERROR, message = error, connectedAt = 0) }
        } else {
            TunnelStatus.update { it.copy(status = Status.DISCONNECTED, message = null, connectedAt = 0) }
        }
        AppLog.i("Disconnected")
        stopForegroundCompat()
        stopSelf()
    }

    private fun userStop() {
        AppLog.i("Disconnect requested")
        stopRequested = true
        TunnelStatus.set(Status.DISCONNECTING)
        val running = job
        if (running == null || !running.isActive) {
            scope.launch { shutdown(null) }
            return
        }
        // Closing sockets unblocks any pending network call; the loop then shuts down.
        scope.launch { closeConnections() }
    }

    override fun onRevoke() {
        AppLog.w("VPN permission revoked (another VPN was started or it was turned off in settings)")
        userStop()
    }

    override fun onDestroy() {
        stopRequested = true
        closeConnections()
        if (hevRunning) runCatching { TProxyNative.TProxyStopService() }
        hevRunning = false
        tun?.let { runCatching { it.close() } }
        tun = null
        if (TunnelStatus.state.value.status.isActive) {
            TunnelStatus.update { it.copy(status = Status.DISCONNECTED, connectedAt = 0) }
        }
        scope.cancel()
        super.onDestroy()
    }

    // ───────────────────────── helpers ─────────────────────────

    private fun userAgent(): String = Payload.DEFAULT_UA

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, TunnelVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, App.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(profile?.name ?: getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_stop, getString(R.string.disconnect), disconnect)
            .build()
    }

    private var lastUp = 0L
    private var lastDown = 0L
    private var lastSample = 0L

    private fun updateNotification() {
        val st = TunnelStatus.state.value
        val text = when (st.status) {
            Status.CONNECTED -> {
                val now = System.currentTimeMillis()
                val up = TunnelStatus.bytesUp.get()
                val down = TunnelStatus.bytesDown.get()
                val dt = (now - lastSample).coerceAtLeast(1)
                val upSpeed = if (lastSample == 0L) 0 else (up - lastUp) * 1000 / dt
                val downSpeed = if (lastSample == 0L) 0 else (down - lastDown) * 1000 / dt
                lastUp = up; lastDown = down; lastSample = now
                "Connected · ↑ ${formatSpeed(upSpeed)} ↓ ${formatSpeed(downSpeed)} · ${formatBytes(up + down)}"
            }
            else -> st.status.label + (st.message?.let { " · $it" } ?: "") + "…"
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        }
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
    }

    companion object {
        const val ACTION_CONNECT = "com.payloadtunnel.action.CONNECT"
        const val ACTION_DISCONNECT = "com.payloadtunnel.action.DISCONNECT"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val NOTIF_ID = 1
        private const val MTU = 8500
        private const val TUN_IPV4 = "198.18.0.1"
        private const val MAPDNS_IP = "198.18.0.2"

        /** hev-socks5-tunnel configuration (YAML). */
        fun tun2socksConfig(socksPort: Int, remoteDns: Boolean): String = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $MTU")
            appendLine("  ipv4: $TUN_IPV4")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            if (remoteDns) {
                // Built-in DNS answers with placeholder IPs from 100.64.0.0/10 and sends the
                // real host name in the SOCKS5 request, so the SSH server resolves it.
                appendLine("mapdns:")
                appendLine("  address: $MAPDNS_IP")
                appendLine("  port: 53")
                appendLine("  network: 100.64.0.0")
                appendLine("  netmask: 255.192.0.0")
                appendLine("  cache-size: 10000")
            }
            appendLine("misc:")
            appendLine("  task-stack-size: 81920")
            appendLine("  connect-timeout: 15000")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }

        fun connect(context: Context, profileId: String) {
            val i = Intent(context, TunnelVpnService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_PROFILE_ID, profileId)
            ContextCompat.startForegroundService(context, i)
        }

        fun disconnect(context: Context) {
            val i = Intent(context, TunnelVpnService::class.java).setAction(ACTION_DISCONNECT)
            try {
                context.startService(i)
            } catch (e: Exception) {
                TunnelStatus.set(Status.DISCONNECTED)
            }
        }
    }
}
