package com.payloadtunnel.core

import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * TLS client used for SSH-TLS and VLESS. Like HTTP Custom / v2ray "allowInsecure", certificates
 * are not verified: the SNI is often a decoy host (e.g. google.com) that does not match the server.
 */
object Tls {
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val context: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
    }

    /** Wraps a connected socket in TLS with the given SNI and completes the handshake. */
    fun wrap(plain: Socket, sni: String, port: Int, timeoutMs: Int = 15_000): SSLSocket {
        val ssl = context.socketFactory.createSocket(plain, sni, port, true) as SSLSocket
        val isIp = sni.matches(Regex("[0-9.]+")) || sni.contains(':')
        if (sni.isNotBlank() && !isIp) {
            runCatching {
                val params = ssl.sslParameters
                params.serverNames = listOf(SNIHostName(sni))
                ssl.sslParameters = params
            }
        }
        ssl.soTimeout = timeoutMs
        ssl.startHandshake()
        ssl.soTimeout = 0
        return ssl
    }
}
