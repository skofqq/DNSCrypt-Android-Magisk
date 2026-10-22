package io.github.skofqq.dnscryptmanager

import io.github.skofqq.dnscryptmanager.dns.DnsProbe
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsProbeTest {

    @Test
    fun `query header carries the transaction id and one question`() {
        val query = DnsProbe.buildQuery(0xBEEF, "example.com")

        val header = DnsProbe.parseHeader(query)!!
        assertEquals(0xBEEF, header.transactionId)
        assertFalse(header.isResponse)
        assertEquals(1, header.questionCount)
        assertEquals(0, header.answerCount)
    }

    @Test
    fun `question section encodes labels then A and IN`() {
        val query = DnsProbe.buildQuery(1, "example.com")

        // 12 header bytes + 7example + 3com + root + qtype + qclass
        assertEquals(12 + 1 + 7 + 1 + 3 + 1 + 4, query.size)
        val question = query.copyOfRange(12, query.size)
        assertArrayEquals(
            byteArrayOf(
                7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
                'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
                3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
                0, 0, 1, 0, 1,
            ),
            question,
        )
        // Recursion desired, standard query.
        assertEquals(0x01.toByte(), query[2])
        assertEquals(0x00.toByte(), query[3])
    }

    @Test
    fun `a response is recognised and its rcode read`() {
        val response = byteArrayOf(
            0x12, 0x34,             // id
            0x81.toByte(), 0x83.toByte(), // QR + RD + RA, rcode 3 (NXDOMAIN)
            0x00, 0x01,             // qdcount
            0x00, 0x00,             // ancount
            0x00, 0x00,
            0x00, 0x00,
        )

        val header = DnsProbe.parseHeader(response)!!
        assertTrue(header.isResponse)
        assertEquals(0x1234, header.transactionId)
        assertEquals(3, header.rcode)
        assertEquals(0, header.answerCount)
    }

    @Test
    fun `an answered query reports rcode zero and an answer count`() {
        val response = byteArrayOf(
            0x00, 0x2A,
            0x81.toByte(), 0x80.toByte(),
            0x00, 0x01,
            0x00, 0x02,
            0x00, 0x00,
            0x00, 0x00,
        )

        val header = DnsProbe.parseHeader(response)!!
        assertEquals(0, header.rcode)
        assertEquals(2, header.answerCount)
        assertEquals(42, header.transactionId)
    }

    @Test
    fun `a truncated datagram is not mistaken for a header`() {
        assertNull(DnsProbe.parseHeader(byteArrayOf(0x00, 0x01, 0x02)))
        assertNull(DnsProbe.parseHeader(ByteArray(20), length = 4))
    }

    @Test
    fun `trailing dots and empty labels are handled`() {
        val withDot = DnsProbe.buildQuery(5, "example.com.")
        val withoutDot = DnsProbe.buildQuery(5, "example.com")
        assertArrayEquals(withoutDot, withDot)

        runCatching { DnsProbe.buildQuery(5, "example..com") }
            .also { assertTrue(it.isFailure) }
    }

    @Test
    fun `transaction id must fit in sixteen bits`() {
        assertTrue(runCatching { DnsProbe.buildQuery(0x10000, "example.com") }.isFailure)
        assertTrue(runCatching { DnsProbe.buildQuery(-1, "example.com") }.isFailure)
    }
}
