package com.payloadtunnel.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/** "Share net": expose the tunnel as a SOCKS5 proxy on the LAN so a PC on the same hotspot can use it. */
object ShareNet {
    data class State(val enabled: Boolean = false, val port: Int = 0, val address: String? = null, val active: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    const val DEFAULT_PORT = 8282

    fun setEnabled(on: Boolean) = _state.update { it.copy(enabled = on) }
    fun setRunning(port: Int, address: String?) = _state.update { it.copy(active = true, port = port, address = address) }
    fun setStopped() = _state.update { it.copy(active = false, address = null) }

    private inline fun MutableStateFlow<State>.update(block: (State) -> State) { value = block(value) }

    /** The phone's Wi-Fi / hotspot IPv4 address other devices can reach, or null. */
    fun localAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.startsWith("127.") == false }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
