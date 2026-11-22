package com.payloadtunnel.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** EncryptedSharedPreferences with recovery if the Android keystore entry is broken. */
object SecurePrefs {
    private const val FILE = "secure_credentials"

    fun create(context: Context): SharedPreferences {
        return try {
            open(context)
        } catch (e: Exception) {
            // Happens on some devices after a backup restore or keystore reset.
            Log.w("SecurePrefs", "Encrypted prefs unreadable, recreating", e)
            context.deleteSharedPreferences(FILE)
            try {
                open(context)
            } catch (e2: Exception) {
                // Keystore is unusable on this device: keep the app working rather than crash.
                Log.e("SecurePrefs", "Android keystore unavailable, passwords stored unencrypted", e2)
                context.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun open(context: Context): SharedPreferences {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
