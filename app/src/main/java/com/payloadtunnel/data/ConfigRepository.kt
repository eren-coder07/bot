package com.payloadtunnel.data

import android.content.Context
import android.content.SharedPreferences
import com.payloadtunnel.net.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * Configs fetched from the control server. Nothing is editable or exported here. The decrypted
 * list is cached in EncryptedSharedPreferences so the VPN service can reload after a restart.
 */
class ConfigRepository(context: Context) {
    private val secure: SharedPreferences = SecurePrefs.create(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("configs", Context.MODE_PRIVATE)

    private val _configs = MutableStateFlow(loadCache())
    val configs: StateFlow<List<Profile>> = _configs

    private val _selectedId = MutableStateFlow(
        prefs.getString(K_SELECTED, null)?.takeIf { id -> _configs.value.any { it.id == id } }
            ?: _configs.value.firstOrNull()?.id,
    )
    val selectedId: StateFlow<String?> = _selectedId

    suspend fun refresh(api: ApiClient, key: String) {
        val json = api.fetchConfigsJson(key)
        val list = parse(json)
        secure.edit().putString(K_CACHE, json).apply()
        _configs.value = list
        if (_selectedId.value == null || list.none { it.id == _selectedId.value }) {
            select(list.firstOrNull()?.id)
        }
    }

    fun get(id: String?): Profile? = _configs.value.firstOrNull { it.id == id }
    fun selected(): Profile? = get(_selectedId.value) ?: _configs.value.firstOrNull()

    fun select(id: String?) {
        _selectedId.value = id
        val edit = prefs.edit()
        if (id == null) edit.remove(K_SELECTED) else edit.putString(K_SELECTED, id)
        get(id)?.group?.takeIf { it.isNotBlank() }?.let { edit.putString("last_$it", id) }
        edit.apply()
    }

    fun selectEntry(entry: List<Profile>) {
        val group = entry.first().group
        val last = if (group.isNotBlank()) prefs.getString("last_$group", null) else null
        select(entry.firstOrNull { it.id == last }?.id ?: entry.first().id)
    }

    /** Test hook: injects configs without the network (screenshot tests only, never used at runtime). */
    fun seedForTests(list: List<Profile>) {
        _configs.value = list
        if (_selectedId.value == null || list.none { it.id == _selectedId.value }) {
            select(list.firstOrNull()?.id)
        }
    }

    /** Groups configs for display: each group once (first-seen order), ungrouped alone. */
    fun entries(list: List<Profile> = _configs.value): List<List<Profile>> {
        val out = LinkedHashMap<String, MutableList<Profile>>()
        list.forEach { p -> out.getOrPut(if (p.group.isBlank()) "id:" + p.id else "g:" + p.group) { mutableListOf() } += p }
        return out.values.toList()
    }

    private fun loadCache(): List<Profile> =
        secure.getString(K_CACHE, null)?.let { runCatching { parse(it) }.getOrNull() } ?: emptyList()

    private fun parse(json: String): List<Profile> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { Profile.fromJson(it) } }
    }

    companion object {
        private const val K_CACHE = "configs_cache"
        private const val K_SELECTED = "selected_id"
    }
}
