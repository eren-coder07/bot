package com.payloadtunnel.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    enum class Level { INFO, OK, WARN, ERROR }

    data class Entry(val seq: Long, val time: Long, val level: Level, val message: String) {
        fun format(): String = "${timeFormat.get()!!.format(Date(time))} ${level.name.padEnd(5)} $message"
    }

    private const val MAX_ENTRIES = 2000
    private var seq = 0L
    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    fun i(msg: String) = add(Level.INFO, msg)
    fun ok(msg: String) = add(Level.OK, msg)
    fun w(msg: String) = add(Level.WARN, msg)
    fun e(msg: String) = add(Level.ERROR, msg)

    @Synchronized
    private fun add(level: Level, msg: String) {
        val entry = Entry(seq++, System.currentTimeMillis(), level, msg)
        Log.println(
            when (level) {
                Level.ERROR -> Log.ERROR
                Level.WARN -> Log.WARN
                else -> Log.INFO
            },
            "PayloadTunnel", msg,
        )
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
