package com.payloadtunnel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.payloadtunnel.core.TunnelStatus
import kotlinx.coroutines.delay

/** Live traffic numbers sampled once per second, shared by Home and Speed screens. */
@Stable
class Traffic {
    var now by mutableLongStateOf(System.currentTimeMillis())
    var up by mutableLongStateOf(0L)
    var down by mutableLongStateOf(0L)
    var upSpeed by mutableLongStateOf(0L)
    var downSpeed by mutableLongStateOf(0L)
    var peakDown by mutableLongStateOf(0L)
    var history by mutableStateOf(List(40) { 0L })
}

@Composable
fun rememberTraffic(): Traffic {
    val t = remember { Traffic() }
    LaunchedEffect(Unit) {
        var lastUp = TunnelStatus.bytesUp.get()
        var lastDown = TunnelStatus.bytesDown.get()
        while (true) {
            delay(1000)
            val u = TunnelStatus.bytesUp.get()
            val d = TunnelStatus.bytesDown.get()
            t.now = System.currentTimeMillis()
            t.upSpeed = (u - lastUp).coerceAtLeast(0)
            t.downSpeed = (d - lastDown).coerceAtLeast(0)
            if (d < lastDown) t.peakDown = 0 // counters were reset (new connection)
            t.peakDown = maxOf(t.peakDown, t.downSpeed)
            t.history = (t.history + t.downSpeed).takeLast(40)
            lastUp = u; lastDown = d
            t.up = u; t.down = d
        }
    }
    return t
}
