package com.skofqq.shieldy

import com.skofqq.shieldy.data.LevelFilter
import com.skofqq.shieldy.data.LogLevel
import com.skofqq.shieldy.data.LogLine
import com.skofqq.shieldy.ui.update.ChangelogBlock
import com.skofqq.shieldy.ui.update.ChangelogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogAndChangelogTest {

    @Test
    fun `proxy line splits into timestamp, level and message`() {
        val line = LogLine.parse("[2026-10-22 19:03:20] [NOTICE] Server with the lowest initial latency: x (rtt: 21ms)")
        assertEquals("2026-10-22 19:03:20", line.timestamp)
        assertEquals(LogLevel.NOTICE, line.level)
        assertEquals("Server with the lowest initial latency: x (rtt: 21ms)", line.message)
    }

    @Test
    fun `module line has a timestamp and no level`() {
        val line = LogLine.parse("2026-10-22 19:32:47 dnscrypt-proxy запущен, pid 848")
        assertEquals("2026-10-22 19:32:47", line.timestamp)
        assertNull(line.level)
        assertEquals("dnscrypt-proxy запущен, pid 848", line.message)
    }

    @Test
    fun `level filter keeps what it promises`() {
        val warn = LogLine.parse("[2026-10-22 19:03:20] [WARNING] slow")
        val crit = LogLine.parse("[2026-10-22 19:03:20] [CRITICAL] dead")
        val debug = LogLine.parse("[2026-10-22 19:03:20] [DEBUG] noise")
        val plain = LogLine.parse("no timestamp at all")
        assertEquals(LogLevel.ERROR, crit.level)
        assertTrue(warn.passes(LevelFilter.WARNING))
        assertFalse(warn.passes(LevelFilter.ERROR))
        assertTrue(crit.passes(LevelFilter.ERROR))
        assertFalse(debug.passes(LevelFilter.INFO))
        assertTrue(plain.passes(LevelFilter.INFO))
        assertFalse(plain.passes(LevelFilter.WARNING))
        assertEquals("no timestamp at all", plain.message)
    }

    @Test
    fun `changelog joins wrapped lines and keeps bullets and headings`() {
        val blocks = ChangelogParser.parse(
            """
            # 2.4

            Автозапуск при загрузке
            теперь можно выключить.

            * новый ключ `autostart`. При `false` модуль остаётся
              установленным и включённым
            * проверка живёт в `service.sh`
            """.trimIndent()
        )
        assertEquals(
            listOf(
                ChangelogBlock.Heading("2.4"),
                ChangelogBlock.Paragraph("Автозапуск при загрузке теперь можно выключить."),
                ChangelogBlock.Bullet("новый ключ `autostart`. При `false` модуль остаётся установленным и включённым"),
                ChangelogBlock.Bullet("проверка живёт в `service.sh`"),
            ),
            blocks,
        )
    }
}

class BoxAndManualStopTest {

    @Test
    fun `box_dns and manual_stop parse, and older modules leave them unset`() {
        val new = com.skofqq.shieldy.data.StatusParser.parse(
            """{"running":false,"port":5354,"port6":5354,"box_dns":true,"manual_stop":true,"version":"2.4.1"}"""
        ).getOrThrow()
        assertEquals(true, new.boxDns)
        assertTrue(new.manualStop)
        val old = com.skofqq.shieldy.data.StatusParser.parse(
            """{"running":true,"port":5354,"port6":"","version":"2.4"}"""
        ).getOrThrow()
        assertNull(old.boxDns)
        assertFalse(old.manualStop)
    }
}

class DnsCheckParsingTest {

    @Test
    fun `servers live json and the log fallback agree`() {
        val json = com.skofqq.shieldy.data.LiveServers.parseJson(
            """{"strategy":"p2","live":172,"servers":[{"name":"cs-finland","proto":"DNSCrypt","rtt":28},{"name":"a","proto":"DoH","rtt":21}]}"""
        )!!
        assertEquals(listOf("a", "cs-finland"), json.servers.map { it.name })
        assertEquals(2, json.inUseCount)
        assertEquals(172, json.liveCount)

        val log = listOf(
            "[2026-10-22 22:00:00] [NOTICE] [old] OK (DNSCrypt) - rtt: 5ms",
            "[2026-10-22 22:11:09] [NOTICE] dnscrypt-proxy 2.1.18",
            "[2026-10-22 22:11:10] [NOTICE] [dct-de] OK (DNSCrypt) - rtt: 345ms",
            "[2026-10-22 22:11:10] [NOTICE] [x] OK (DoH) - rtt: 40ms",
            "[2026-10-22 22:11:10] [NOTICE] [x] OK (DoH) - rtt: 30ms - additional certificate",
        )
        val parsed = com.skofqq.shieldy.data.LiveServers.parse(log, "lb_strategy = 'first'\n")
        assertEquals(listOf("x", "dct-de"), parsed.servers.map { it.name })
        assertEquals(30, parsed.servers.first().rttMs)
        assertEquals(1, parsed.inUseCount)
    }

    @Test
    fun `leak verdict flags the own ASN and the network DNS`() {
        val report = com.skofqq.shieldy.data.LeakTest.parse(
            """[{"ip":"1.1.1.1","country":"ru","asn":"AS64500 Example ISP","type":"ip"},{"ip":"9.9.9.9","country":"de","asn":"AS3214 xTom","type":"dns"}]"""
        )
        val clean = com.skofqq.shieldy.data.LeakTest.verdict(report, proxyActive = true, networkDns = listOf("10.0.2.3"))
        assertEquals(com.skofqq.shieldy.data.DnsVerdict.THROUGH_PROXY, clean)
        val leak = com.skofqq.shieldy.data.LeakTest.verdict(report, proxyActive = true, networkDns = listOf("9.9.9.9"))
        assertEquals(com.skofqq.shieldy.data.DnsVerdict.LEAK, leak)
        assertEquals("🇩🇪", report.resolvers.first().flag)
    }
}
