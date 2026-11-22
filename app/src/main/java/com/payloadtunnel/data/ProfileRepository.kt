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
        _profiles.value = load()
        if (prefs.getInt(KEY_BUILTIN_VERSION, 0) < BuiltinServers.VERSION) {
            seedBuiltins(firstRun = !prefs.getBoolean(KEY_INITIALIZED, false))
        }
        _selectedId.value = prefs.getString(KEY_SELECTED, null)
            ?.takeIf { id -> _profiles.value.any { it.id == id } }
            ?: _profiles.value.firstOrNull()?.id
    }

    /** Adds or refreshes the built-in "Free Vip" servers, keeping the user's own profiles. */
    private fun seedBuiltins(firstRun: Boolean) {
        val builtinIds = BuiltinServers.all.map { it.id }.toSet()
        val oldDefault = Profile.defaultProfile()
        var oldDefaultSelected = false
        val kept = _profiles.value.filter { p ->
            if (p.id in builtinIds) return@filter false
            // Drop the untouched single default profile from older versions (now "WIN*6 1").
            val isOldDefault = p.copy(id = oldDefault.id) == oldDefault
            if (isOldDefault && p.id == prefs.getString(KEY_SELECTED, null)) oldDefaultSelected = true
            !isOldDefault
        }
        persist(BuiltinServers.all + kept)
        val edit = prefs.edit().putBoolean(KEY_INITIALIZED, true).putInt(KEY_BUILTIN_VERSION, BuiltinServers.VERSION)
        if (firstRun || oldDefaultSelected || prefs.getString(KEY_SELECTED, null) == null) {
            edit.putString(KEY_SELECTED, BuiltinServers.all.first().id)
        }
        edit.apply()
    }

    /** Profiles grouped for display: each group once (in first-seen order), ungrouped ones alone. */
    fun entries(list: List<Profile> = _profiles.value): List<List<Profile>> {
        val out = LinkedHashMap<String, MutableList<Profile>>()
        list.forEach { p -> out.getOrPut(if (p.group.isBlank()) "id:" + p.id else "g:" + p.group) { mutableListOf() } += p }
        return out.values.toList()
    }

    fun get(id: String?): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun selected(): Profile? = get(_selectedId.value) ?: _profiles.value.firstOrNull()

    fun select(id: String) {
        _selectedId.value = id
        val edit = prefs.edit().putString(KEY_SELECTED, id)
        get(id)?.group?.takeIf { it.isNotBlank() }?.let { edit.putString("last_in_group_$it", id) }
        edit.apply()
    }

    /** Selects a group entry: its last used variant, or the first one. */
    fun selectEntry(entry: List<Profile>) {
        val group = entry.first().group
        val last = if (group.isNotBlank()) prefs.getString("last_in_group_$group", null) else null
        select(entry.firstOrNull { it.id == last }?.id ?: entry.first().id)
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
        val copy = src.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = if (src.group.isNotBlank()) "${src.group} ${src.name}" else src.name + " (copy)",
            group = "",
        )
        save(copy)
        return copy
    }

    /** Returns the number of imported profiles (see [ConfigImport] for the accepted formats). */
    fun importJson(text: String): Int {
        val existingIds = _profiles.value.map { it.id }.toSet()
        val parsed = ConfigImport.parse(text)
        parsed.forEach { p0 ->
            val p = if (p0.id in existingIds) p0.copy(id = java.util.UUID.randomUUID().toString()) else p0
            save(p)
        }
        return parsed.size
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
        private const val KEY_BUILTIN_VERSION = "builtin_version"
    }
}
