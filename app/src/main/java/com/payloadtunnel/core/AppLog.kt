package com.payloadtunnel.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Two channels:
 *  - the user log ([entries]) shown on the Log screen — only friendly, non-sensitive lines
 *    posted via [user]/[step]/[good]/[warn]/[fail].
 *  - debug ([i]/[ok]/[w]/[e]/[dbg]) which goes to logcat only. The connection core uses these,
 *    so payloads, hosts, proxies and server responses never reach the user log or the UI.
 */
object AppLog {
    enum class Level { INFO, OK, WARN, ERROR }

    data class Entry(val seq: Long, val time: Long, val level: Level, val message: String) {
        fun format(): String = "${timeFormat.get()!!.format(Date(time))}  $message"
    }

    private const val MAX_ENTRIES = 500
    private const val TAG = "PayloadTunnel"
    private var seq = 0L
    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm:ss", Locale.US)
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    // ---- user channel (visible) ----
    fun user(level: Level, msg: String) = addUser(level, msg)
    fun step(msg: String) = addUser(Level.INFO, msg)
    fun good(msg: String) = addUser(Level.OK, msg)
    fun warn(msg: String) = addUser(Level.WARN, msg)
    fun fail(msg: String) = addUser(Level.ERROR, msg)

    // ---- debug channel (logcat only) ----
    fun i(msg: String) = Log.i(TAG, msg)
    fun ok(msg: String) = Log.i(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String) = Log.e(TAG, msg)
    fun dbg(msg: String) = Log.d(TAG, msg)

    @Synchronized
    private fun addUser(level: Level, msg: String) {
        val entry = Entry(seq++, System.currentTimeMillis(), level, msg)
        Log.println(if (level == Level.ERROR) Log.ERROR else Log.INFO, TAG, msg)
        _entries.update { old ->
            val list = if (old.size >= MAX_ENTRIES) old.drop(old.size - MAX_ENTRIES + 1) else old
            list + entry
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun asText(): String = _entries.value.joinToString("\n") { it.format() }
}
