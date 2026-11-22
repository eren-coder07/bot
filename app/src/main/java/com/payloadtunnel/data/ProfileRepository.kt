package com.payloadtunnel.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Profiles are stored as JSON in normal SharedPreferences WITHOUT passwords.
 * Passwords live in EncryptedSharedPreferences, keyed by profile id.
 */
class ProfileRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = SecurePrefs.create(context)

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId

    init {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            val def = Profile.defaultProfile()
            persist(listOf(def))
            prefs.edit().putBoolean(KEY_INITIALIZED, true).putString(KEY_SELECTED, def.id).apply()
        }
        _profiles.value = load()
        _selectedId.value = prefs.getString(KEY_SELECTED, null)
            ?.takeIf { id -> _profiles.value.any { it.id == id } }
            ?: _profiles.value.firstOrNull()?.id
    }

    fun get(id: String?): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun selected(): Profile? = get(_selectedId.value) ?: _profiles.value.firstOrNull()

    fun select(id: String) {
        _selectedId.value = id
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    @Synchronized
    fun save(profile: Profile) {
        val list = _profiles.value.toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        persist(list)
        if (_selectedId.value == null) select(profile.id)
    }

    @Synchronized
    fun delete(id: String) {
        val list = _profiles.value.filterNot { it.id == id }
        secure.edit().remove(pwKey(id)).apply()
        persist(list)
        if (_selectedId.value == id) {
            list.firstOrNull()?.let { select(it.id) } ?: run {
                _selectedId.value = null
                prefs.edit().remove(KEY_SELECTED).apply()
            }
        }
    }

    fun duplicate(id: String): Profile? {
        val src = get(id) ?: return null
        val copy = src.copy(id = java.util.UUID.randomUUID().toString(), name = src.name + " (copy)")
        save(copy)
        return copy
    }

    /** Returns the number of imported profiles. Accepts a single object, an array, or {"profiles": [...]}. */
    fun importJson(text: String): Int {
        val trimmed = text.trim()
        val arr: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("profiles") ?: JSONArray().put(obj)
            }
        }
        var count = 0
        val existingIds = _profiles.value.map { it.id }.toSet()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var p = Profile.fromJson(o)
            if (p.id in existingIds) p = p.copy(id = java.util.UUID.randomUUID().toString())
            if (p.sshHost.isBlank()) continue
            save(p)
            count++
        }
        return count
    }

    fun exportJson(ids: Collection<String>? = null): String {
        val arr = JSONArray()
        _profiles.value.filter { ids == null || it.id in ids }.forEach { arr.put(it.toJson(includePassword = true)) }
        return JSONObject().put("app", "PayloadTunnel").put("version", 1).put("profiles", arr).toString(2)
    }

    private fun load(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    val p = Profile.fromJson(o)
                    p.copy(password = secure.getString(pwKey(p.id), "") ?: "")
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<Profile>) {
        val arr = JSONArray()
        val secureEdit = secure.edit()
        list.forEach {
            arr.put(it.toJson(includePassword = false))
            secureEdit.putString(pwKey(it.id), it.password)
        }
        secureEdit.apply()
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
        _profiles.value = list
    }

    private fun pwKey(id: String) = "pw_$id"

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_SELECTED = "selected_id"
        private const val KEY_INITIALIZED = "initialized"
    }
}
