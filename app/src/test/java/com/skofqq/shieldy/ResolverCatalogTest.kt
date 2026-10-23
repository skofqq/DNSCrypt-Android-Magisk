package com.skofqq.shieldy

import com.skofqq.shieldy.data.DnsStamp
import com.skofqq.shieldy.data.ResolverParser
import com.skofqq.shieldy.data.ServerSelection
import com.skofqq.shieldy.data.StampProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ResolverCatalogTest {

    // ------------------------------------------------------------ stamps

    private fun stamp(protocol: Int, props: Long?, tail: ByteArray = byteArrayOf(1, 2, 3)): String {
        val body = ArrayList<Byte>()
        body.add(protocol.toByte())
        if (props != null) {
            for (i in 0 until 8) body.add(((props shr (8 * i)) and 0xFF).toByte())
        }
        tail.forEach { body.add(it) }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray())
        return "sdns://$encoded"
    }

    @Test
    fun `dnscrypt stamp reports dnssec and no logs`() {
        val info = DnsStamp.decode(stamp(0x01, 3L))!!

        assertEquals(StampProtocol.DNSCRYPT, info.protocol)
        assertTrue(info.dnssec)
        assertTrue(info.noLog)
        assertFalse(info.noFilter)
    }

    @Test
    fun `doh stamp with all three flags`() {
        val info = DnsStamp.decode(stamp(0x02, 7L))!!

        assertEquals(StampProtocol.DOH, info.protocol)
        assertTrue(info.dnssec)
        assertTrue(info.noLog)
        assertTrue(info.noFilter)
    }

    @Test
    fun `relay stamp has no props and must not be read as one`() {
        // Address bytes right after the protocol byte; reading them as props would
        // produce a nonsense number instead of "no information".
        val info = DnsStamp.decode(
            stamp(0x81, null, byteArrayOf(0x13, 0x31, 0x37, 0x36, 0x2E, 0x31, 0x30, 0x33, 0x2E, 0x31))
        )!!

        assertEquals(StampProtocol.DNSCRYPT_RELAY, info.protocol)
        assertTrue(info.protocol.isRelay)
        assertNull(info.props)
        assertFalse(info.dnssec)
        assertFalse(info.noLog)
        assertFalse(info.noFilter)
    }

    @Test
    fun `odoh relay is recognised too`() {
        val info = DnsStamp.decode(stamp(0x85, null))!!

        assertEquals(StampProtocol.ODOH_RELAY, info.protocol)
        assertNull(info.props)
    }

    @Test
    fun `broken base64 does not crash`() {
        assertNull(DnsStamp.decode("sdns://!!!not base64!!!"))
        assertNull(DnsStamp.decode("sdns://"))
        assertNull(DnsStamp.decode(""))
        assertNull(DnsStamp.decode("https://example.com"))
    }

    @Test
    fun `padding is restored before decoding`() {
        // Five bytes encode to eight base64 characters minus padding.
        val info = DnsStamp.decode(stamp(0x01, 3L, byteArrayOf(9)))
        assertNotNull(info)
        assertEquals(StampProtocol.DNSCRYPT, info!!.protocol)
    }

    @Test
    fun `truncated stamp yields protocol without props`() {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(0x02, 1, 2))
        val info = DnsStamp.decode("sdns://$encoded")!!

        assertEquals(StampProtocol.DOH, info.protocol)
        assertNull(info.props)
    }

    // ------------------------------------------------------- markdown

    private val markdown = """
        # Public resolvers

        A preamble that belongs to no block.

        ## adguard-dns

        AdGuard DNS Default public resolver.
        Blocks ads, trackers and malware.

        Second paragraph of the description.

        ${stamp(0x01, 3L)}
        ${stamp(0x01, 3L, byteArrayOf(4, 5))}

        ## no-stamp-server

        Someone forgot the stamp here.

        ## quad9

        Quad9 in the Netherlands.

        ${stamp(0x02, 7L)}
    """.trimIndent()

    @Test
    fun `blocks are split on the heading line`() {
        val entries = ResolverParser.parse(markdown)

        assertEquals(listOf("adguard-dns", "no-stamp-server", "quad9"), entries.map { it.name })
    }

    @Test
    fun `a block keeps every stamp but takes properties from the first`() {
        val entry = ResolverParser.parse(markdown).first()

        assertEquals(2, entry.stamps.size)
        assertEquals(StampProtocol.DNSCRYPT, entry.protocol)
        assertTrue(entry.dnssec)
    }

    @Test
    fun `multi-paragraph description is kept and summarised by its first line`() {
        val entry = ResolverParser.parse(markdown).first()

        assertEquals("AdGuard DNS Default public resolver.", entry.summary)
        assertTrue(entry.description.contains("Second paragraph"))
        assertTrue(entry.description.contains("Blocks ads"))
    }

    @Test
    fun `a block without a stamp survives with unknown protocol`() {
        val entry = ResolverParser.parse(markdown)[1]

        assertEquals("no-stamp-server", entry.name)
        assertTrue(entry.stamps.isEmpty())
        assertEquals(StampProtocol.UNKNOWN, entry.protocol)
        assertFalse(entry.dnssec)
    }

    @Test
    fun `the last block in the file is not dropped`() {
        val entries = ResolverParser.parse(markdown)

        assertEquals("quad9", entries.last().name)
        assertEquals(1, entries.last().stamps.size)
        assertEquals(StampProtocol.DOH, entries.last().protocol)
    }

    @Test
    fun `search covers the description, not just the name`() {
        val quad9 = ResolverParser.parse(markdown).last()

        assertTrue(quad9.matches("Netherlands"))
        assertTrue(quad9.matches("quad"))
        assertFalse(quad9.matches("Iceland"))
    }

    @Test
    fun `empty input yields no entries`() {
        assertTrue(ResolverParser.parse("").isEmpty())
        assertTrue(ResolverParser.parse("no headings here\njust text").isEmpty())
    }

    // ------------------------------------------------- servers set argument

    @Test
    fun `set argument keeps the order it was given`() {
        val argument = ServerSelection.buildSetArgument(listOf("quad9", "adguard-dns", "cloudflare"))

        assertEquals("quad9,adguard-dns,cloudflare", argument.getOrThrow())
    }

    @Test
    fun `duplicates are dropped, first position wins`() {
        val argument = ServerSelection.buildSetArgument(
            listOf("quad9", "adguard-dns", "quad9", " adguard-dns ")
        )

        assertEquals("quad9,adguard-dns", argument.getOrThrow())
    }

    @Test
    fun `an illegal name is rejected instead of quoted into the command`() {
        assertTrue(ServerSelection.buildSetArgument(listOf("quad9; reboot")).isFailure)
        assertTrue(ServerSelection.buildSetArgument(listOf("quad9", "a b")).isFailure)
        assertTrue(ServerSelection.buildSetArgument(listOf("../etc/passwd")).isFailure)
        assertTrue(ServerSelection.buildSetArgument(emptyList()).isFailure)
        assertTrue(ServerSelection.buildSetArgument(listOf(" ")).isFailure)
    }

    @Test
    fun `dotted and dashed names are legal`() {
        assertTrue(ServerSelection.isValidName("dnscrypt.eu-nl"))
        assertTrue(ServerSelection.isValidName("nextdns-abc123"))
        assertTrue(ServerSelection.isValidName("quad9_dnscrypt"))
    }

    @Test
    fun `auto and blank mean no manual selection`() {
        assertTrue(ServerSelection.parseSelection("auto").isEmpty())
        assertTrue(ServerSelection.parseSelection("AUTO").isEmpty())
        assertTrue(ServerSelection.parseSelection("   ").isEmpty())
        assertEquals(
            listOf("quad9", "adguard-dns"),
            ServerSelection.parseSelection(" quad9 , adguard-dns "),
        )
    }
}
