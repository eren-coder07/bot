package com.payloadtunnel.core

/**
 * Expands HTTP-injector style payload tags.
 *
 * Supported tags (case-insensitive):
 *   [crlf] [lf] [cr] [lfcr]          line breaks
 *   [host] [port] [host_port] [ssh]  SSH server host / port / host:port
 *   [ua]                             User-Agent
 *   [protocol]                       HTTP/1.1
 *   [split]                          send what came before as a separate write, then wait a short delay
 *   [delay_split]                    like [split] but with a longer delay
 *   [instant_split]                  separate write without any delay
 *
 * Real line breaks typed in the editor are ignored; use [crlf].
 */
object Payload {
    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    const val SPLIT_DELAY_MS = 300L
    const val LONG_SPLIT_DELAY_MS = 1000L

    data class Part(val bytes: ByteArray, val delayAfterMs: Long)

    val TAGS = listOf("[crlf]", "[lf]", "[cr]", "[host]", "[port]", "[host_port]", "[ua]", "[split]", "[protocol]")

    private val splitRegex = Regex("\\[(split|delay_split|instant_split)]", RegexOption.IGNORE_CASE)
    private val tagRegex = Regex(
        "\\[(crlf|lfcr|lf|cr|host_port|ssh|host|port|ua|protocol)]",
        RegexOption.IGNORE_CASE,
    )

    fun expand(text: String, host: String, port: Int, ua: String): String =
        tagRegex.replace(text) { m ->
            when (m.groupValues[1].lowercase()) {
                "crlf" -> "\r\n"
                "lfcr" -> "\n\r"
                "lf" -> "\n"
                "cr" -> "\r"
                "host_port", "ssh" -> "$host:$port"
                "host" -> host
                "port" -> port.toString()
                "ua" -> ua
                "protocol" -> "HTTP/1.1"
                else -> m.value
            }
        }

    fun build(template: String, host: String, port: Int, ua: String = DEFAULT_UA): List<Part> {
        val clean = template.replace("\r", "").replace("\n", "")
        val parts = mutableListOf<Part>()
        var last = 0
        for (m in splitRegex.findAll(clean)) {
            val delay = when (m.groupValues[1].lowercase()) {
                "delay_split" -> LONG_SPLIT_DELAY_MS
                "instant_split" -> 0L
                else -> SPLIT_DELAY_MS
            }
            val chunk = clean.substring(last, m.range.first)
            if (chunk.isNotEmpty()) parts += Part(expand(chunk, host, port, ua).toByteArray(Charsets.ISO_8859_1), delay)
            last = m.range.last + 1
        }
        val tail = clean.substring(last)
        if (tail.isNotEmpty()) parts += Part(expand(tail, host, port, ua).toByteArray(Charsets.ISO_8859_1), 0L)
        return parts
    }

    /** Printable version of an expanded payload for the log. */
    fun printable(bytes: ByteArray): String =
        String(bytes, Charsets.ISO_8859_1).replace("\r\n", "\\r\\n").replace("\n", "\\n").replace("\r", "\\r")
}
