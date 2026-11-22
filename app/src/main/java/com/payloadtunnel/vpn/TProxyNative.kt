package com.payloadtunnel.vpn

/**
 * JNI bridge to hev-socks5-tunnel (2.20.x). The native library registers these methods on
 * com/payloadtunnel/vpn/TProxyNative (see cFlags in app/build.gradle.kts); the signatures
 * must match src/hev-jni.c exactly or the library fails to load.
 */
object TProxyNative {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    external fun TProxyStopService(): Boolean

    external fun TProxyIsRunning(): Boolean

    external fun TProxyGetStats(): LongArray?
}
