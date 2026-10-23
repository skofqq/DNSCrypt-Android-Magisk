package com.skofqq.shieldy.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/** Outcome of one UDP round trip to the local proxy. No root involved. */
sealed interface ProbeResult {
    data class Answered(
        val rttMillis: Long,
        val answerCount: Int,
        val rcode: Int,
    ) : ProbeResult {
        val isResolved: Boolean get() = rcode == 0 && answerCount > 0
    }

    data object TimedOut : ProbeResult

    data class Failed(val reason: String) : ProbeResult
}

/** Header fields of a DNS message, as far as the probe cares. */
data class DnsHeader(
    val transactionId: Int,
    val isResponse: Boolean,
    val rcode: Int,
    val questionCount: Int,
    val answerCount: Int,
)

/**
 * A hand-rolled DNS client, small enough that pulling in a library would cost more
 * than it saves: one A query out, one answer in, round trip measured.
 */
object DnsProbe {

    const val DEFAULT_HOST = "example.com"
    const val TIMEOUT_MILLIS = 2_000
    private const val HEADER_SIZE = 12
    private const val MAX_RESPONSE = 1500

    /** Builds an A/IN query with recursion desired. */
    fun buildQuery(transactionId: Int, host: String): ByteArray {
        require(transactionId in 0..0xFFFF) { "Transaction id out of range" }
        val labels = host.trim().trim('.').split('.')
        require(labels.all { it.isNotEmpty() && it.length <= 63 }) { "Invalid host: $host" }

        val body = ArrayList<Byte>(HEADER_SIZE + host.length + 6)
        body.add((transactionId shr 8).toByte())
        body.add((transactionId and 0xFF).toByte())
        body.add(0x01)  // flags: standard query, recursion desired
        body.add(0x00)
        body.add(0x00); body.add(0x01)  // QDCOUNT = 1
        body.add(0x00); body.add(0x00)  // ANCOUNT
        body.add(0x00); body.add(0x00)  // NSCOUNT
        body.add(0x00); body.add(0x00)  // ARCOUNT

        labels.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            body.add(bytes.size.toByte())
            bytes.forEach { body.add(it) }
        }
        body.add(0x00)                  // root label
        body.add(0x00); body.add(0x01)  // QTYPE = A
        body.add(0x00); body.add(0x01)  // QCLASS = IN

        return body.toByteArray()
    }

    /** Reads the 12-byte header. Returns null when the datagram is too short to be one. */
    fun parseHeader(packet: ByteArray, length: Int = packet.size): DnsHeader? {
        if (length < HEADER_SIZE) return null
        fun u16(offset: Int) = ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
        val flags = u16(2)
        return DnsHeader(
            transactionId = u16(0),
            isResponse = (flags and 0x8000) != 0,
            rcode = flags and 0x000F,
            questionCount = u16(4),
            answerCount = u16(6),
        )
    }

    /**
     * Sends one query to 127.0.0.1:[port] and waits for a reply carrying the same
     * transaction id. Everything here blocks, so it runs on [Dispatchers.IO].
     */
    suspend fun probe(
        port: Int,
        host: String = DEFAULT_HOST,
        timeoutMillis: Int = TIMEOUT_MILLIS,
    ): ProbeResult = withContext(Dispatchers.IO) {
        val transactionId = Random.nextInt(0, 0x10000)
        val query = runCatching { buildQuery(transactionId, host) }
            .getOrElse { return@withContext ProbeResult.Failed(it.message ?: "bad query") }

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = timeoutMillis
            val loopback = InetAddress.getByName("127.0.0.1")
            val started = System.nanoTime()
            socket.send(DatagramPacket(query, query.size, loopback, port))

            val buffer = ByteArray(MAX_RESPONSE)
            val deadline = started + timeoutMillis * 1_000_000L
            while (System.nanoTime() < deadline) {
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val header = parseHeader(buffer, response.length) ?: continue
                if (!header.isResponse || header.transactionId != transactionId) continue
                val rtt = (System.nanoTime() - started) / 1_000_000
                return@withContext ProbeResult.Answered(rtt, header.answerCount, header.rcode)
            }
            ProbeResult.TimedOut
        } catch (timeout: SocketTimeoutException) {
            ProbeResult.TimedOut
        } catch (error: Exception) {
            ProbeResult.Failed(error.message ?: error.javaClass.simpleName)
        } finally {
            socket?.close()
        }
    }
}
