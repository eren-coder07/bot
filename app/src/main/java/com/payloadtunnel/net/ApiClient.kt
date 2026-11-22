package com.payloadtunnel.net

import com.payloadtunnel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

/** Error carrying the server's user-facing message (shown in the app). */
class ApiException(val code: String, override val message: String, val http: Int = 0) : IOException(message)

/**
 * Talks to the control server. All config/auth/time goes through here. Supports optional TLS
 * public-key pinning (BuildConfig.CERT_PIN_SHA256, base64 SHA-256 of the server SPKI).
 */
class ApiClient(
    private val baseUrl: String = BuildConfig.SERVER_URL.trimEnd('/'),
    private val pin: String = BuildConfig.CERT_PIN_SHA256,
) {
    data class Auth(
        val label: String, val timeRemaining: Long, val freeSessionSeconds: Long,
        val adRewardSeconds: Long, val startAppId: String, val announcement: String,
    )

    suspend fun ping(): JSONObject = request("GET", "/api/v1/ping", null, null)

    suspend fun auth(key: String, deviceId: String): Auth {
        val o = request("POST", "/api/v1/auth", key, JSONObject().put("device_id", deviceId))
        return Auth(
            o.optString("label"), o.optLong("timeRemaining"), o.optLong("freeSessionSeconds", 3600),
            o.optLong("adRewardSeconds", 3600), o.optString("startAppId"), o.optString("announcement"),
        )
    }

    /** Returns the decrypted configs JSON (array string). */
    suspend fun fetchConfigsJson(key: String): String {
        val o = request("GET", "/api/v1/configs", key, null)
        val plain = ConfigCrypto.decrypt(BuildConfig.APP_SECRET, key, o.getString("enc"))
        return String(plain, Charsets.UTF_8)
    }

    /** Returns time remaining (seconds) and whether it is expired. */
    suspend fun heartbeat(key: String, connected: Boolean): Pair<Long, Boolean> {
        val o = request("POST", "/api/v1/session/heartbeat", key, JSONObject().put("connected", connected))
        return o.optLong("timeRemaining") to o.optBoolean("expired")
    }

    suspend fun adReward(key: String, nonce: String): Long {
        val o = request("POST", "/api/v1/ad/reward", key, JSONObject().put("nonce", nonce))
        return o.optLong("timeRemaining")
    }

    private suspend fun request(method: String, path: String, key: String?, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "PayloadTunnel/${BuildConfig.VERSION_NAME}")
                if (key != null) conn.setRequestProperty("X-Api-Key", key)
                if (conn is HttpsURLConnection && pin.isNotBlank()) {
                    conn.connect()
                    verifyPin(conn)
                }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (status in 200..299) {
                    if (text.isBlank()) JSONObject() else JSONObject(text)
                } else {
                    throw parseError(status, text)
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                throw ApiException("network", "Can't reach the server. Check your connection.", 0)
            } finally {
                conn.disconnect()
            }
        }

    private fun parseError(status: Int, text: String): ApiException {
        // FastAPI puts our JSON error in "detail".
        val detail = runCatching { JSONObject(text).opt("detail") }.getOrNull()
        val obj = runCatching {
            when (detail) {
                is String -> JSONObject(detail)
                is JSONObject -> detail
                else -> null
            }
        }.getOrNull()
        if (obj != null) {
            return ApiException(obj.optString("error", "error"), obj.optString("message", "Request failed"), status)
        }
        val msg = when (status) {
            401 -> "Please enter your access key."
            403 -> "Access denied."
            429 -> "Too many requests, try again shortly."
            else -> "Server error ($status)."
        }
        return ApiException("http", msg, status)
    }

    private fun verifyPin(conn: HttpsURLConnection) {
        val cert = conn.serverCertificates.firstOrNull() ?: throw ApiException("pin", "Secure connection failed.")
        val spki = cert.publicKey.encoded
        val digest = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(spki))
        val pins = pin.split(",").map { it.trim() }
        if (digest !in pins) throw ApiException("pin", "Secure connection check failed.")
    }
}
