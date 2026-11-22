package com.payloadtunnel.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class Status(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    SENDING_PAYLOAD("Sending payload"),
    AUTHENTICATING("Authenticating"),
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting"),
    DISCONNECTING("Disconnecting"),
    ERROR("Error");

    val isActive: Boolean
        get() = this != DISCONNECTED && this != ERROR
}

data class TunnelState(
    val status: Status = Status.DISCONNECTED,
    val message: String? = null,
    val profileName: String? = null,
    val connectedAt: Long = 0L,
    val socksPort: Int = 0,
    val serverVersion: String? = null,
    /** Round trip of opening a channel through SSH, or -1 if unknown. */
    val pingMs: Long = -1,
)

/** Process-wide connection state and traffic counters. */
object TunnelStatus {
    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state

    val bytesUp = AtomicLong()
    val bytesDown = AtomicLong()

    fun set(status: Status, message: String? = null) = _state.update { it.copy(status = status, message = message) }

    fun update(transform: (TunnelState) -> TunnelState) = _state.update(transform)

    fun resetTraffic() {
        bytesUp.set(0)
        bytesDown.set(0)
    }
}
