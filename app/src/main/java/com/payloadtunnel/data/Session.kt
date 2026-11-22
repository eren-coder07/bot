package com.payloadtunnel.data

import android.content.Context
import android.content.SharedPreferences
import com.payloadtunnel.net.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** Access key, device id and live time-credit state. The key lives in EncryptedSharedPreferences. */
class Session(context: Context) {
    enum class State { NO_KEY, CHECKING, VALID, INVALID, BANNED, OFFLINE }

    private val secure: SharedPreferences = SecurePrefs.create(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    val deviceId: String = secure.getString(K_DEVICE, null) ?: UUID.randomUUID().toString().also {
        secure.edit().putString(K_DEVICE, it).apply()
    }

    private val _key = MutableStateFlow(secure.getString(K_KEY, null)?.takeIf { it.isNotBlank() })
    val key: StateFlow<String?> = _key

    private val _state = MutableStateFlow(if (_key.value == null) State.NO_KEY else State.CHECKING)
    val state: StateFlow<State> = _state

    private val _timeRemaining = MutableStateFlow(prefs.getLong(K_TIME, 0))
    val timeRemaining: StateFlow<Long> = _timeRemaining

    private val _announcement = MutableStateFlow(prefs.getString(K_ANNOUNCE, "") ?: "")
    val announcement: StateFlow<String> = _announcement

    @Volatile var label: String = prefs.getString(K_LABEL, "") ?: ""
        private set
    @Volatile var startAppId: String = prefs.getString(K_STARTAPP, "") ?: ""
        private set
    @Volatile var adRewardSeconds: Long = prefs.getLong(K_ADREWARD, 3600)
        private set

    fun hasKey(): Boolean = !_key.value.isNullOrBlank()

    fun saveKey(k: String) {
        val key = k.trim()
        secure.edit().putString(K_KEY, key).apply()
        _key.value = key
        _state.value = State.CHECKING
    }

    fun clearKey() {
        secure.edit().remove(K_KEY).apply()
        prefs.edit().clear().apply()
        _key.value = null
        _timeRemaining.value = 0
        _state.value = State.NO_KEY
    }

    fun setState(s: State) {
        _state.value = s
    }

    fun applyAuth(a: ApiClient.Auth) {
        label = a.label
        startAppId = a.startAppId.ifBlank { startAppId }
        adRewardSeconds = a.adRewardSeconds
        _timeRemaining.value = a.timeRemaining
        _announcement.value = a.announcement
        prefs.edit()
            .putString(K_LABEL, label).putString(K_STARTAPP, startAppId)
            .putLong(K_ADREWARD, adRewardSeconds).putLong(K_TIME, a.timeRemaining)
            .putString(K_ANNOUNCE, a.announcement).apply()
        _state.value = State.VALID
    }

    fun setTime(seconds: Long) {
        _timeRemaining.value = seconds.coerceAtLeast(0)
        prefs.edit().putLong(K_TIME, _timeRemaining.value).apply()
    }

    fun effectiveStartAppId(): String = startAppId.ifBlank { com.payloadtunnel.BuildConfig.START_APP_ID }

    companion object {
        private const val K_KEY = "access_key"
        private const val K_DEVICE = "device_id"
        private const val K_TIME = "time_remaining"
        private const val K_LABEL = "label"
        private const val K_STARTAPP = "start_app_id"
        private const val K_ADREWARD = "ad_reward_seconds"
        private const val K_ANNOUNCE = "announcement"
    }
}
