package com.payloadtunnel.core

import java.io.InputStream
import java.io.OutputStream

/** A bidirectional stream to one destination through the tunnel. */
interface StreamChannel {
    val input: InputStream
    val output: OutputStream
    fun close()
}

/** Something that can open TCP streams to arbitrary destinations (SSH session, VLESS server). */
interface Upstream {
    val isConnected: Boolean
    val description: String
    fun open(host: String, port: Int, timeoutMs: Int = 15_000): StreamChannel
    fun close()
}
