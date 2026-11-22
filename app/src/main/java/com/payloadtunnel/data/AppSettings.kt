package com.payloadtunnel.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppFilterMode(val label: String) {
    ALL("All apps use the tunnel"),
    INCLUDE("Only selected apps use the tunnel"),
    EXCLUDE("All apps except selected"),
}

data class Settings(
    val autoReconnect: Boolean = true,
    /** 0 = unlimited. */
    val maxRetries: Int = 0,
    val appFilterMode: AppFilterMode = AppFilterMode.ALL,
    val selectedApps: Set<String> = emptySet(),
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state
    val current: Settings get() = _state.value

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_state.value)
        prefs.edit()
            .putBoolean("autoReconnect", s.autoReconnect)
            .putInt("maxRetries", s.maxRetries)
            .putString("appFilterMode", s.appFilterMode.name)
            .putStringSet("selectedApps", s.selectedApps)
            .apply()
        _state.value = s
    }

    private fun load() = Settings(
        autoReconnect = prefs.getBoolean("autoReconnect", true),
        maxRetries = prefs.getInt("maxRetries", 0),
        appFilterMode = runCatching {
            AppFilterMode.valueOf(prefs.getString("appFilterMode", AppFilterMode.ALL.name)!!)
        }.getOrDefault(AppFilterMode.ALL),
        selectedApps = prefs.getStringSet("selectedApps", emptySet())?.toSet() ?: emptySet(),
    )
}
