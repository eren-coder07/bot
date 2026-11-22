package com.payloadtunnel.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.security.NetworkSecurityPolicy
import com.payloadtunnel.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * Best-effort device-integrity checks: root, repackaging (signature), debugger, and live traffic
 * interception (system proxy / known capture apps like Reqable, HttpCanary, Packet Capture).
 *
 * These raise the bar; they are not unbeatable. Certificate pinning on the control server is the
 * real guarantee that a MITM can't read configs — these catch the common capture setups early.
 */
object SecurityGuard {

    data class Status(
        val rooted: Boolean,
        val signatureTampered: Boolean,
        val debugger: Boolean,
        val captureActive: Boolean,
        val captureApp: String?,
    ) {
        /** A blocking reason to show the user, or null when the environment looks clean. */
        fun block(): String? = when {
            signatureTampered -> "This copy of the app has been modified or re-signed. Install the official version."
            rooted -> "This device appears to be rooted. For your safety the VPN is disabled here."
            captureApp != null -> "A traffic-capture app ($captureApp) is installed. Remove it to continue."
            captureActive -> "A network proxy / capture tool is active. Turn it off to continue."
            debugger && !BuildConfig.DEBUG -> "A debugger is attached. Close it to continue."
            else -> null
        }
    }

    fun check(context: Context): Status = Status(
        rooted = isRooted(),
        signatureTampered = isSignatureTampered(context),
        debugger = Debug.isDebuggerConnected(),
        captureActive = isProxyActive(),
        captureApp = findCaptureApp(context),
    )

    private val SU_PATHS = arrayOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sd/xbin/su",
        "/system/bin/failsafe/su", "/data/local/su", "/data/local/xbin/su", "/data/local/bin/su",
        "/system/app/Superuser.apk", "/system/xbin/daemonsu", "/dev/com.koushikdutta.superuser.daemon/",
        "/sbin/magisk", "/data/adb/magisk", "/data/adb/modules",
    )

    private fun isRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        if (SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) }) return true
        // `which su` on PATH
        return System.getenv("PATH")?.split(":")?.any {
            runCatching { File(it, "su").exists() }.getOrDefault(false)
        } ?: false
    }

    private fun isSignatureTampered(context: Context): Boolean {
        val expected = BuildConfig.EXPECTED_SIG
        if (expected.isBlank()) return false // self-check disabled (e.g. debug builds)
        return currentSignatureSha256(context).none { it.equals(expected, ignoreCase = true) }
    }

    fun currentSignatureSha256(context: Context): List<String> = try {
        val pm = context.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION", "PackageManagerGetSignatures")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures ?: emptyArray()
        }
        sigs.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                .joinToString("") { b -> "%02x".format(b) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun isProxyActive(): Boolean {
        val host = System.getProperty("http.proxyHost")
        val port = System.getProperty("http.proxyPort")
        return !host.isNullOrBlank() && !port.isNullOrBlank()
    }

    private val CAPTURE_PACKAGES = mapOf(
        "com.guoshi.httpcanary" to "HttpCanary",
        "com.guoshi.httpcanary.premium" to "HttpCanary",
        "com.minhui.networkcapture" to "Packet Capture",
        "com.minhui.networkcapture.pro" to "Packet Capture",
        "app.greyshirts.sslcapture" to "SSL Capture",
        "com.egloos.scps960.packetcapture" to "Packet Capture",
        "com.reqable.android" to "Reqable",
        "io.tempage.dorado" to "Thor",
        "jp.co.taosoftware.android.packetcapture" to "tPacketCapture",
        "com.httptoolkit.android.v1" to "HTTP Toolkit",
        "com.packagesampler.proxy" to "Proxyman",
        "lyrebirdstudio.capture" to "Capture",
    )

    private fun findCaptureApp(context: Context): String? {
        val pm = context.packageManager
        for ((pkg, name) in CAPTURE_PACKAGES) {
            if (runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)) return name
        }
        return null
    }

    /** True if cleartext HTTP is permitted (we expect HTTPS to the control server). */
    fun cleartextAllowed(): Boolean =
        runCatching { NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted }.getOrDefault(true)
}
