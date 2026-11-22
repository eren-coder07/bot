package com.payloadtunnel.core

import android.util.Log
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Step 4: a local SOCKS5 server on 127.0.0.1 whose CONNECT requests are carried over SSH
 * direct-tcpip channels (dynamic port forwarding, like "ssh -D").
 *
 * UDP ASSOCIATE is supported only for DNS: UDP datagrams to port 53 are converted to
 * DNS-over-TCP queries through the SSH tunnel. Other UDP traffic cannot be carried by SSH
 * and is dropped.
 */
class Socks5Server(private val ssh: SshConnection) {
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private var server: ServerSocket? = null
    private val pool: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "socks-${threadIds.incrementAndGet()}").apply { isDaemon = true }
    }
    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val clients = Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<java.io.Closeable, Boolean>())
    private val running = AtomicBoolean(false)
    private val udpDropWarned = AtomicBoolean(false)
    private val failedConnects = AtomicInteger(0)

    var port: Int = 0
        private set

    /** Binds the preferred port, or automatically another free one if it is busy. Returns the bound port. */
    fun start(preferredPort: Int): Int {
        val candidates = buildList {
            if (preferredPort in 1..65535) {
                add(preferredPort)
                for (i in 1..20) if (preferredPort + i <= 65535) add(preferredPort + i)
            }
            add(0) // let the OS choose any free port
        }
        var lastError: Exception? = null
        for (p in candidates) {
            val ss = ServerSocket()
            try {
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(loopback, p), 128)
                server = ss
                port = ss.localPort
                if (p != preferredPort) {
                    AppLog.w("Local port $preferredPort is busy, using free port $port instead")
                }
                break
            } catch (e: IOException) {
                runCatching { ss.close() }
                lastError = e
                if (p == preferredPort) AppLog.w("Local port $p unavailable (${e.message}), trying another port")
            }
        }
        val ss = server ?: throw BindException("Could not bind any local SOCKS port: ${lastError?.message}")
        running.set(true)
        Thread({ acceptLoop(ss) }, "socks-accept").apply { isDaemon = true }.start()
        return port
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        clients.toList().forEach { runCatching { it.close() } }
        clients.clear()
        pool.shutdownNow()
        timer.shutdownNow()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running.get()) {
            val client = try {
                ss.accept()
            } catch (e: IOException) {
                if (running.get()) AppLog.e("SOCKS server stopped: ${e.message}")
                break
            }
            clients += client
            try {
                pool.execute { handle(client) }
            } catch (e: Exception) {
                closeQuietly(client)
            }
        }
    }

    private fun handle(client: Socket) {
        try {
            client.tcpNoDelay = true
            client.soTimeout = 30_000
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()

            // Greeting
            if (input.readUnsignedByte() != 5) return closeQuietly(client)
            val nMethods = input.readUnsignedByte()
            input.readFully(ByteArray(nMethods))
            output.write(byteArrayOf(5, 0))
            output.flush()

            // Request
            if (input.readUnsignedByte() != 5) return closeQuietly(client)
            val cmd = input.readUnsignedByte()
            input.readUnsignedByte() // RSV
            val (host, port) = readAddress(input)

            when (cmd) {
                1 -> handleConnect(client, input, output, host, port)
                3 -> handleUdpAssociate(client, input, output)
                else -> {
                    reply(output, 7)
                    closeQuietly(client)
                }
            }
        } catch (e: Exception) {
            closeQuietly(client)
        }
    }

    private fun handleConnect(client: Socket, input: InputStream, output: OutputStream, host: String, port: Int) {
        val channel = try {
            ssh.openDirect(host, port)
        } catch (e: Exception) {
            val n = failedConnects.incrementAndGet()
            if (n <= 5 || n % 50 == 0) AppLog.w("Server could not open $host:$port (${e.message})")
            runCatching { reply(output, 5) }
            closeQuietly(client)
            return
        }
        val channelCloser = java.io.Closeable { channel.close() }
        clients += channelCloser
        reply(output, 0)
        client.soTimeout = 0

        val remaining = AtomicInteger(2)
        val finish = {
            if (remaining.decrementAndGet() == 0) {
                clients -= channelCloser
                channel.close()
                closeQuietly(client)
            }
        }
        // client -> server (upload)
        pool.execute {
            val buf = ByteArray(BUF)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    channel.output.write(buf, 0, n)
                    channel.output.flush()
                    TunnelStatus.bytesUp.addAndGet(n.toLong())
                }
                runCatching { channel.output.close() } // half-close: send EOF
                finish()
            } catch (e: Exception) {
                channel.close()
                closeQuietly(client)
                finish()
            }
        }
        // server -> client (download), on this thread
        val buf = ByteArray(BUF)
        try {
            while (true) {
                val n = channel.input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
                TunnelStatus.bytesDown.addAndGet(n.toLong())
            }
            runCatching { client.shutdownOutput() }
            finish()
        } catch (e: Exception) {
            channel.close()
            closeQuietly(client)
            finish()
        }
    }

    private fun handleUdpAssociate(client: Socket, input: InputStream, output: OutputStream) {
        val udp = DatagramSocket(InetSocketAddress(loopback, 0))
        clients += udp
        val p = udp.localPort
        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, (p shr 8).toByte(), p.toByte()))
        output.flush()

        pool.execute { udpLoop(udp) }
        // The association lives as long as the control TCP connection.
        client.soTimeout = 0
        try {
            while (input.read() >= 0) { /* ignore */ }
        } catch (_: IOException) {
        }
        udp.close()
        clients -= udp
        closeQuietly(client)
    }

    private fun udpLoop(udp: DatagramSocket) {
        val buf = ByteArray(65_535)
        while (!udp.isClosed) {
            val packet = DatagramPacket(buf, buf.size)
            try {
                udp.receive(packet)
            } catch (e: IOException) {
                break
            }
            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            val sender = packet.socketAddress
            if (data.size < 10 || data[2].toInt() != 0) continue // fragmented datagrams unsupported
            val parsed = try {
                parseUdpHeader(data)
            } catch (e: Exception) {
                continue
            } ?: continue
            val (headerLen, host, port) = parsed
            if (port != 53) {
                if (udpDropWarned.compareAndSet(false, true)) {
                    AppLog.w("UDP to $host:$port dropped: SSH tunnels carry TCP only (DNS is forwarded over TCP)")
                }
                continue
            }
            val header = data.copyOfRange(0, headerLen)
            val query = data.copyOfRange(headerLen, data.size)
            TunnelStatus.bytesUp.addAndGet(query.size.toLong())
            try {
                pool.execute {
                    val answer = dnsOverTcp(host, query) ?: return@execute
                    TunnelStatus.bytesDown.addAndGet(answer.size.toLong())
                    val out = header + answer
                    runCatching { udp.send(DatagramPacket(out, out.size, sender)) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun dnsOverTcp(server: String, query: ByteArray): ByteArray? {
        var channel: DirectChannel? = null
        val watchdog = timer.schedule({ channel?.close() }, 10, TimeUnit.SECONDS)
        return try {
            channel = ssh.openDirect(server, 53, 10_000)
            val out = channel.output
            out.write(byteArrayOf((query.size shr 8).toByte(), query.size.toByte()) + query)
            out.flush()
            val din = DataInputStream(channel.input)
            val len = din.readUnsignedShort()
            ByteArray(len).also { din.readFully(it) }
        } catch (e: Exception) {
            Log.d("Socks5", "DNS via $server failed: ${e.message}")
            null
        } finally {
            watchdog.cancel(false)
            channel?.close()
        }
    }

    /** Returns header length, host, port of a SOCKS5 UDP request header. */
    private fun parseUdpHeader(d: ByteArray): Triple<Int, String, Int>? {
        var i = 3
        val host: String
        when (d[i++].toInt()) {
            1 -> {
                host = InetAddress.getByAddress(d.copyOfRange(i, i + 4)).hostAddress!!; i += 4
            }
            3 -> {
                val len = d[i++].toInt() and 0xff
                host = String(d, i, len, Charsets.US_ASCII); i += len
            }
            4 -> {
                host = InetAddress.getByAddress(d.copyOfRange(i, i + 16)).hostAddress!!; i += 16
            }
            else -> return null
        }
        val port = ((d[i].toInt() and 0xff) shl 8) or (d[i + 1].toInt() and 0xff)
        return Triple(i + 2, host, port)
    }

    private fun readAddress(input: DataInputStream): Pair<String, Int> {
        val host = when (input.readUnsignedByte()) {
            1 -> ByteArray(4).also { input.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
            3 -> ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            4 -> ByteArray(16).also { input.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
            else -> throw IOException("Bad address type")
        }
        return host to input.readUnsignedShort()
    }

    private fun reply(out: OutputStream, code: Int) {
        out.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        out.flush()
    }

    private fun closeQuietly(s: Socket) {
        clients -= s
        try {
            s.close()
        } catch (_: SocketException) {
        } catch (_: IOException) {
        }
    }

    companion object {
        private const val BUF = 32 * 1024
        private val threadIds = AtomicInteger()
    }
}
