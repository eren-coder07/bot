package com.payloadtunnel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.payloadtunnel.data.AppSettings
import com.payloadtunnel.data.ProfileRepository

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_VPN, getString(R.string.channel_vpn), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }

    companion object {
        const val CHANNEL_VPN = "vpn_status"
    }
}

/** Tiny service locator shared by the UI and the VPN service (same process). */
object Graph {
    lateinit var profiles: ProfileRepository
        private set
    lateinit var settings: AppSettings
        private set

    fun init(app: Application) {
        profiles = ProfileRepository(app)
        settings = AppSettings(app)
    }
}
