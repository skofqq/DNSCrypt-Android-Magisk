package io.github.skofqq.dnscryptmanager

import io.github.skofqq.dnscryptmanager.data.ActiveServer
import io.github.skofqq.dnscryptmanager.data.Ipv6Dns
import io.github.skofqq.dnscryptmanager.data.ModuleUpdater
import io.github.skofqq.dnscryptmanager.data.StatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleUpdateParsingTest {

    @Test
    fun `port6 parses as string, number and null`() {
        fun port6(value: String) = StatusParser
            .parse("""{"running":true,"port":5354,"port6":$value}""").getOrThrow().port6
        assertEquals(5354, port6("\"5354\""))
        assertEquals(5354, port6("5354"))
        assertNull(port6("null"))
        assertNull(port6("\"\""))
    }

    @Test
    fun `ipv6_dns is optional and drives the bypass flag`() {
        val old = StatusParser.parse("""{"running":true,"port":5354}""").getOrThrow()
        assertNull(old.ipv6Dns)
        assertFalse(old.ipv6Bypass)

        val leak = StatusParser.parse("""{"running":true,"ipv6_dns":"bypass"}""").getOrThrow()
        assertEquals(Ipv6Dns.BYPASS, leak.ipv6Dns)
        assertTrue(leak.ipv6Bypass)

        val stopped = StatusParser.parse("""{"running":false,"ipv6_dns":"bypass"}""").getOrThrow()
        assertFalse(stopped.ipv6Bypass)

        assertEquals(Ipv6Dns.BLOCKED, StatusParser.parse("""{"ipv6_dns":"blocked"}""").getOrThrow().ipv6Dns)
        assertNull(StatusParser.parse("""{"ipv6_dns":"something-new"}""").getOrThrow().ipv6Dns)
    }

    @Test
    fun `active server line is split into name, rtt and live count`() {
        val line = "[2026-10-22 19:03:20] [NOTICE] Server with the lowest initial latency: " +
            "dnscry.pt-helsinki-ipv4 (rtt: 21ms), live servers: 173"
        val parsed = ActiveServer.parse(line)!!
        assertEquals("dnscry.pt-helsinki-ipv4", parsed.name)
        assertEquals(21, parsed.rttMs)
        assertEquals(173, parsed.liveServers)
        assertNull(ActiveServer.parse("nothing useful here"))
    }

    @Test
    fun `update json needs version, code and an https zip`() {
        val ok = ModuleUpdater.parseUpdateJson(
            """{"version":"2.4.2","versionCode":242000,
               "zipUrl":"https://github.com/x/y/releases/download/2.4.2/m.zip",
               "changelog":"https://raw.githubusercontent.com/x/y/main/CHANGELOG.md"}"""
        )!!
        assertEquals("2.4.2", ok.version)
        assertEquals(242000L, ok.versionCode)
        assertEquals("https://raw.githubusercontent.com/x/y/main/CHANGELOG.md", ok.changelogUrl)

        assertNull(ModuleUpdater.parseUpdateJson("""{"version":"2.5","versionCode":250000,"zipUrl":"http://x/m.zip"}"""))
        assertNull(ModuleUpdater.parseUpdateJson("""{"version":"2.5","zipUrl":"https://x/m.zip"}"""))
        assertNull(ModuleUpdater.parseUpdateJson("not json"))
    }

    @Test
    fun `changelog keeps only sections above the installed version`() {
        val changelog = "# 2.4.2\n\nfix\n\n# 2.4.1\n\nolder\n\n# 2.4\n\noldest"
        assertEquals("# 2.4.2\n\nfix", ModuleUpdater.newerSections(changelog, "2.4.1"))
        assertNull(ModuleUpdater.newerSections(changelog, "2.4.2"))
    }

    @Test
    fun `every popular abi maps to a binary in the zip`() {
        assertEquals("arm64", ModuleUpdater.binaryArch("arm64-v8a"))
        assertEquals("arm", ModuleUpdater.binaryArch("armeabi-v7a"))
        assertEquals("x86_64", ModuleUpdater.binaryArch("x86_64"))
        assertEquals("i386", ModuleUpdater.binaryArch("x86"))
        assertNull(ModuleUpdater.binaryArch("riscv64"))
    }
}
