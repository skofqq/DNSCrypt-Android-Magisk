package com.skofqq.shieldy

import com.skofqq.shieldy.data.BlocklistFormat
import com.skofqq.shieldy.data.LogDomains
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistFormatTest {

    @Test
    fun acceptsCommonFormats() {
        assertEquals("ads.example.com", BlocklistFormat.domain("ads.example.com"))
        assertEquals("ads.example.com", BlocklistFormat.domain("*.ads.example.com"))
        assertEquals("ads.example.com", BlocklistFormat.domain("0.0.0.0 ads.example.com"))
        assertEquals("ads.example.com", BlocklistFormat.domain("127.0.0.1\tADS.Example.com # tracker"))
        assertEquals("ads.example.com", BlocklistFormat.domain("||ads.example.com^"))
    }

    @Test
    fun rejectsNoise() {
        assertNull(BlocklistFormat.domain("# comment"))
        assertNull(BlocklistFormat.domain("! adblock comment"))
        assertNull(BlocklistFormat.domain("[Adblock Plus 2.0]"))
        assertNull(BlocklistFormat.domain("0.0.0.0 localhost"))
        assertNull(BlocklistFormat.domain("10.0.0.1"))
        assertNull(BlocklistFormat.domain("not a domain"))
        assertNull(BlocklistFormat.domain("example.com; rm -rf /"))
        assertNull(BlocklistFormat.domain("nodot"))
    }

    @Test
    fun isDomainGuardsShellInput() {
        assertTrue(BlocklistFormat.isDomain("tracker.example.org"))
        assertFalse(BlocklistFormat.isDomain("Tracker.example.org"))
        assertFalse(BlocklistFormat.isDomain("a.com'; reboot; '"))
    }

    @Test
    fun logDomainsSkipFileNames() {
        val line = "[2026-10-23 14:12:21] [NOTICE] Loading the set of blocking rules from [blocked-names.txt] for ads.example.com"
        assertEquals(listOf("ads.example.com"), LogDomains.extract(line))
        assertEquals(
            listOf("dnscry.pt", "doh.example.net"),
            LogDomains.extract("resolver dnscry.pt via doh.example.net. config dnscrypt-proxy.toml"),
        )
        assertTrue(LogDomains.extract("2026-10-23 dnscrypt-proxy запущен, pid 8018").isEmpty())
    }
}
