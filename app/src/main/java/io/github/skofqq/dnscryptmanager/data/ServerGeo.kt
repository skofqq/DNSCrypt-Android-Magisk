package io.github.skofqq.dnscryptmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Offline IPv4 → country lookup over DB-IP "IP to Country Lite" (CC BY 4.0, db-ip.com),
 * converted by tools/geoip-build.js into `assets/geoip4.bin`:
 * "GEO4", u16 n, n two-letter codes, u32 m, m × (u32 range start, u8 code index; 255 = none).
 */
class GeoIp private constructor(private val codes: List<String>, private val starts: IntArray, private val index: ByteArray) {

    /** Two-letter code, or null for unallocated space and anything not IPv4. */
    fun country(address: InetAddress): String? {
        if (address !is Inet4Address) return null
        val raw = address.address
        val ip = ((raw[0].toLong() and 0xFF) shl 24) or ((raw[1].toLong() and 0xFF) shl 16) or
            ((raw[2].toLong() and 0xFF) shl 8) or (raw[3].toLong() and 0xFF)
        // Starts are stored as unsigned 32-bit numbers; compare them as such.
        var low = 0
        var high = starts.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if ((starts[mid].toLong() and 0xFFFFFFFFL) <= ip) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (found < 0) return null
        val code = index[found].toInt() and 0xFF
        return if (code == 255) null else codes.getOrNull(code)?.takeIf { it != "ZZ" }
    }

    companion object {
        private const val ASSET = "geoip4.bin"

        @Volatile
        private var instance: GeoIp? = null

        fun load(context: Context): GeoIp = instance ?: synchronized(this) {
            instance ?: read(context.assets.open(ASSET).use { it.readBytes() }).also { instance = it }
        }

        fun read(bytes: ByteArray): GeoIp {
            val buffer = ByteBuffer.wrap(bytes)
            val magic = ByteArray(4).also { buffer.get(it) }
            require(String(magic, Charsets.US_ASCII) == "GEO4") { "Not a GEO4 table" }
            val codeCount = buffer.short.toInt() and 0xFFFF
            val codes = List(codeCount) {
                val pair = ByteArray(2).also { buffer.get(it) }
                String(pair, Charsets.US_ASCII)
            }
            val count = buffer.int
            val starts = IntArray(count)
            val index = ByteArray(count)
            for (i in 0 until count) {
                starts[i] = buffer.int
                index[i] = buffer.get()
            }
            return GeoIp(codes, starts, index)
        }
    }
}

/** Where a resolver's stamp points: a literal address and/or a hostname to resolve. */
data class StampAddress(val ip: String?, val host: String?)

object StampAddresses {

    /**
     * Reads the address part of an sdns:// stamp. DNSCrypt: props, LP(addr), …;
     * DoH/DoT/DoQ: props, LP(addr), VLP(hashes), LP(hostname), …; ODoH target:
     * props, LP(hostname), …; relays: LP(addr).
     */
    fun of(stamp: String): StampAddress? {
        val bytes = decode(stamp) ?: return null
        if (bytes.isEmpty()) return null
        val protocol = StampProtocol.of(bytes[0].toInt() and 0xFF)
        val reader = Reader(bytes, 1)
        return runCatching {
            when (protocol) {
                StampProtocol.DNSCRYPT_RELAY, StampProtocol.ODOH_RELAY -> StampAddress(host(reader.lp()), null)
                StampProtocol.DNSCRYPT, StampProtocol.PLAIN -> {
                    reader.skip(8)
                    StampAddress(host(reader.lp()), null)
                }
                StampProtocol.DOH, StampProtocol.DOT, StampProtocol.DOQ -> {
                    reader.skip(8)
                    val addr = host(reader.lp())
                    reader.vlp()
                    StampAddress(addr, hostOnly(reader.lp()))
                }
                StampProtocol.ODOH_TARGET -> {
                    reader.skip(8)
                    StampAddress(null, hostOnly(reader.lp()))
                }
                StampProtocol.UNKNOWN -> null
            }
        }.getOrNull()
    }

    /** "1.2.3.4:443" → "1.2.3.4", "[2a00::1]:443" → "2a00::1", "" → null. */
    private fun host(addr: String): String? {
        if (addr.isBlank()) return null
        if (addr.startsWith("[")) return addr.substringAfter('[').substringBefore(']')
        return if (addr.count { it == ':' } == 1) addr.substringBefore(':') else addr
    }

    private fun hostOnly(name: String): String? = name.substringBefore(':').ifBlank { null }

    private fun decode(stamp: String): ByteArray? {
        val payload = stamp.trim().removePrefix("sdns://").trimEnd('=')
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        return runCatching { java.util.Base64.getUrlDecoder().decode(padded) }.getOrNull()
    }

    private class Reader(private val bytes: ByteArray, private var pos: Int) {
        fun skip(n: Int) {
            pos += n
            require(pos <= bytes.size)
        }

        fun lp(): String {
            val length = bytes[pos].toInt() and 0xFF
            val text = String(bytes, pos + 1, length, Charsets.US_ASCII)
            pos += 1 + length
            return text
        }

        /** Variable-length set: each item's length byte has the high bit set while more follow. */
        fun vlp() {
            while (true) {
                val b = bytes[pos].toInt() and 0xFF
                pos += 1 + (b and 0x7F)
                if (b and 0x80 == 0) break
            }
        }
    }
}

/** Countries of the resolvers in the catalogue; null code means unknown or anycast. */
object ServerCountries {

    /** Code for servers whose location cannot be told offline (IPv6-only, anycast hosts, lookups that failed). */
    const val UNKNOWN = "??"

    suspend fun map(entries: List<ResolverEntry>, geo: GeoIp): Map<String, String> = withContext(Dispatchers.IO) {
        coroutineScope {
            entries.map { entry ->
                async {
                    entry.name to (countryOf(entry, geo) ?: UNKNOWN)
                }
            }.awaitAll().toMap()
        }
    }

    private suspend fun countryOf(entry: ResolverEntry, geo: GeoIp): String? {
        val address = entry.stamps.firstNotNullOfOrNull { StampAddresses.of(it) } ?: return null
        address.ip?.let { literal ->
            val parsed = runCatching { InetAddress.getByName(literal) }.getOrNull()
            if (parsed != null) return geo.country(parsed)
        }
        // DoH and ODoH name a host instead; one lookup through whatever resolves now.
        val host = address.host ?: return null
        val resolved = withTimeoutOrNull(3_000) {
            runCatching { InetAddress.getAllByName(host).firstOrNull { it is Inet4Address } }.getOrNull()
        } ?: return null
        return geo.country(resolved)
    }

    /** Regional-indicator flag for a two-letter code; a globe for the unknown group. */
    fun flag(code: String): String =
        code.uppercase().takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
            ?.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }?.joinToString("") ?: "🌐"
}
