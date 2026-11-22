package com.payloadtunnel.vpn

/**
 * JNI bridge to hev-socks5-tunnel. The native library registers these methods on
 * com/payloadtunnel/vpn/TProxyNative (see cFlags in app/build.gradle.kts).
 */
object TProxyNative {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    external fun TProxyStartService(configPath: String, fd: Int)

    external fun TProxyStopService()

    external fun TProxyGetStats(): LongArray?
}
