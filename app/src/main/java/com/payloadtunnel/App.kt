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
        runCatching { com.startapp.sdk.adsbase.StartAppSDK.init(this, Graph.session.effectiveStartAppId(), false) }
        runCatching { com.startapp.sdk.adsbase.StartAppAd.disableSplash() }
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

    /** Registers this device (gets/keeps its time) and loads configs. Returns null or an error. */
    suspend fun refresh(): String? {
        val id = session.deviceId
        return try {
            session.applyAuth(api.register(id))
            runCatching { configs.refresh(api, id) }
            null
        } catch (e: ApiException) {
            session.setState(if (e.code == "banned") Session.State.BANNED else Session.State.OFFLINE)
            e.message
        }
    }
}
