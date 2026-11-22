package com.payloadtunnel.net

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM, matching the server (key = SHA256("APP_SECRET:accessKey")). */
object ConfigCrypto {
    fun decrypt(appSecret: String, accessKey: String, blob: String): ByteArray {
        val key = MessageDigest.getInstance("SHA-256").digest("$appSecret:$accessKey".toByteArray())
        val raw = Base64.getDecoder().decode(blob)
        val nonce = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ct)
    }
}
