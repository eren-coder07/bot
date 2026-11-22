package com.payloadtunnel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.payloadtunnel.data.ConfigRepository
import com.payloadtunnel.data.Session
import com.payloadtunnel.net.ApiClient
import com.payloadtunnel.net.ApiException

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_VPN, getString(R.string.channel_vpn), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
        try {
            com.startapp.sdk.adsbase.StartAppSDK.init(this, Graph.session.effectiveStartAppId(), false)
        } catch (e: Exception) {
            com.payloadtunnel.core.AppLog.dbg("StartApp init failed: ${e.message}")
        }
        runCatching { com.startapp.sdk.adsbase.StartAppAd.disableSplash() }
        runCatching { com.startapp.sdk.adsbase.StartAppAd.enableConsent(this, false) }
        runCatching { com.startapp.sdk.adsbase.StartAppSDK.setTestAdsEnabled(false) }
    }

    companion object {
        const val CHANNEL_VPN = "vpn_status"
    }
}

/** Service locator shared by the UI and the VPN service (same process). */
object Graph {
    lateinit var configs: ConfigRepository
        private set
    lateinit var session: Session
        private set
    lateinit var api: ApiClient
        private set

    fun init(app: Application) {
        session = Session(app)
        configs = ConfigRepository(app)
        api = ApiClient()
    }

    /** Validates the key, loads configs. Returns null on success or a user-facing error. */
    suspend fun signIn(key: String): String? {
        session.saveKey(key)
        return refresh()
    }

    /** Re-checks the key and refreshes configs. Returns null on success or an error message. */
    suspend fun refresh(): String? {
        val key = session.key.value ?: return "No access key."
        return try {
            val auth = api.auth(key, session.deviceId)
            session.applyAuth(auth)
            runCatching { configs.refresh(api, key) }
            null
        } catch (e: ApiException) {
            when (e.code) {
                "banned" -> session.setState(Session.State.BANNED)
                "invalid", "device" -> session.setState(Session.State.INVALID)
                "network" -> session.setState(Session.State.OFFLINE)
                else -> session.setState(Session.State.OFFLINE)
            }
            e.message
        }
    }
}
