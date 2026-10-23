package com.skofqq.shieldy

import com.skofqq.shieldy.data.ModuleContract
import com.skofqq.shieldy.data.ModuleVersion
import com.skofqq.shieldy.data.StatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleStatusParserTest {

    @Test
    fun `parses a running status`() {
        val json = """
            {"running":true,"pid":1234,"supervisor_pid":1200,"port":5354,"port6":"5354",
            "rules_v4":true,"redirect_ipv6":"auto","tether_redirect":false,
            "private_dns":"off","config":"/data/adb/dnscrypt-proxy/dnscrypt-proxy.toml",
            "version":"2.1.18-r1"}
        """.trimIndent().replace("\n", "")

        val status = StatusParser.parse(json).getOrThrow()

        assertTrue(status.running)
        assertEquals(1234, status.pid)
        assertEquals(1200, status.supervisorPid)
        assertEquals(5354, status.port)
        assertEquals(5354, status.port6)
        assertTrue(status.rulesV4)
        assertEquals("auto", status.redirectIpv6)
        assertFalse(status.tetherRedirect)
        assertEquals("off", status.privateDns)
        assertEquals("2.1.18-r1", status.version)
        assertFalse(status.isRestarting)
    }

    @Test
    fun `empty port6 and private_dns mean no data`() {
        val json = """{"running":false,"pid":null,"supervisor_pid":null,"port":5354,"port6":"",""" +
            """"rules_v4":false,"redirect_ipv6":"off","tether_redirect":false,"private_dns":"",""" +
            """"config":"/data/adb/dnscrypt-proxy/dnscrypt-proxy.toml","version":"2.1.18-r1"}"""

        val status = StatusParser.parse(json).getOrThrow()

        assertNull(status.port6)
        assertNull(status.privateDns)
        assertNull(status.pid)
        assertFalse(status.running)
    }

    @Test
    fun `unknown fields from newer module releases are ignored`() {
        val json = """{"running":true,"pid":7,"supervisor_pid":6,"port":5354,"port6":"5354",""" +
            """"rules_v4":true,"redirect_ipv6":"redirect","tether_redirect":true,""" +
            """"private_dns":"hostname","config":"/x.toml","version":"2.2.0-r3",""" +
            """"doh_servers":42,"future_flag":{"nested":true}}"""

        val status = StatusParser.parse(json).getOrThrow()

        assertEquals(7, status.pid)
        assertTrue(status.tetherRedirect)
        assertEquals("redirect", status.redirectIpv6)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val status = StatusParser.parse("""{"running":false}""").getOrThrow()

        assertEquals(ModuleContract.DEFAULT_PORT, status.port)
        assertEquals("auto", status.redirectIpv6)
        assertNull(status.port6)
        assertNull(status.versionOrNull)
    }

    @Test
    fun `noise before the json line is skipped`() {
        val raw = "warning: something\n{\"running\":true,\"pid\":9,\"port\":5355}"

        val status = StatusParser.parse(raw).getOrThrow()

        assertEquals(5355, status.port)
        assertEquals(9, status.pid)
    }

    @Test
    fun `output without json fails instead of throwing`() {
        assertTrue(StatusParser.parse("dnscrypt-ctl: unknown command").isFailure)
    }

    @Test
    fun `supervisor alive without proxy reads as restarting`() {
        val status = StatusParser.parse("""{"running":false,"pid":null,"supervisor_pid":88}""").getOrThrow()

        assertTrue(status.isRestarting)
    }

    @Test
    fun `revision numbers compare as numbers, not as strings`() {
        assertTrue(ModuleVersion.isAtLeast("2.1.18-r3", "2.1.18-r2"))
        assertTrue(ModuleVersion.isAtLeast("2.1.18-r10", "2.1.18-r9"))
        assertFalse(ModuleVersion.isAtLeast("2.1.18-r9", "2.1.18-r10"))
        assertEquals(0, ModuleVersion.compare("2.1.18-r3", "2.1.18-r3"))
    }

    @Test
    fun `a version without the r suffix still parses`() {
        assertEquals(listOf(2, 1, 18, 0), ModuleVersion.parse("2.1.18"))
        assertEquals(listOf(2, 1, 18, 3), ModuleVersion.parse("v2.1.18-r3"))
        assertEquals(listOf(0, 0), ModuleVersion.parse(""))
        assertTrue(ModuleVersion.isAtLeast("2.1.18-r1", "2.1.18"))
    }

    @Test
    fun `capability gates follow the module version`() {
        val r1 = StatusParser.parse("""{"running":true,"version":"2.1.18-r1"}""").getOrThrow()
        val r2 = StatusParser.parse("""{"running":true,"version":"2.1.18-r2"}""").getOrThrow()
        val r3 = StatusParser.parse("""{"running":true,"version":"2.1.18-r3"}""").getOrThrow()

        assertFalse(r1.supportsServers)
        assertTrue(r2.supportsServers)
        assertFalse(r2.supportsConflicts)
        assertTrue(r3.supportsServers)
        assertTrue(r3.supportsConflicts)
    }

    @Test
    fun `run_gid stays hidden until the release where it works`() {
        fun status(version: String) =
            StatusParser.parse("""{"running":true,"version":"$version"}""").getOrThrow()

        // r3 and 2.2 accept the key but the module cannot apply it.
        assertFalse(status("2.1.18-r3").supportsRunGid)
        assertFalse(status("2.2").supportsRunGid)
        assertTrue(status("2.2.1").supportsRunGid)
        assertTrue(status("2.3").supportsRunGid)
        assertTrue(status("2.2.1-r1").supportsRunGid)
    }

    @Test
    fun `mirror controls disappear once the module stops using shared storage`() {
        fun status(version: String) =
            StatusParser.parse("""{"running":true,"version":"$version"}""").getOrThrow()

        assertTrue(status("2.2").supportsMirror)
        assertTrue(status("2.2.1").supportsMirror)
        assertFalse(status("2.3").supportsMirror)
        assertFalse(status("2.4").supportsMirror)
    }

    @Test
    fun `resetting every setting at once needs the release that added the command`() {
        fun status(version: String) =
            StatusParser.parse("""{"running":true,"version":"$version"}""").getOrThrow()

        assertFalse(status("2.3").supportsReset)
        assertTrue(status("2.3.1").supportsReset)
        assertTrue(status("2.4").supportsReset)
    }

    @Test
    fun `turning off the boot start needs the release that added the key`() {
        fun status(version: String) =
            StatusParser.parse("""{"running":true,"version":"$version"}""").getOrThrow()

        assertFalse(status("2.3.1").supportsAutostart)
        assertTrue(status("2.4").supportsAutostart)
        assertTrue(status("2.5").supportsAutostart)
    }

    @Test
    fun `servers field is read as a selection or as auto`() {
        val auto = StatusParser.parse("""{"running":true,"servers":"auto"}""").getOrThrow()
        val picked = StatusParser.parse(
            """{"running":true,"servers":"quad9,adguard-dns","resolvers_cached":true}"""
        ).getOrThrow()

        assertTrue(auto.serversAuto)
        assertFalse(auto.resolversCached)
        assertEquals(listOf("quad9", "adguard-dns"), picked.serverSelection)
        assertFalse(picked.serversAuto)
        assertTrue(picked.resolversCached)
    }

    @Test
    fun `version comparison follows dotted numbers and the r revision`() {
        assertTrue(ModuleVersion.isAtLeast("2.1.18-r1", ModuleContract.MIN_VERSION))
        assertTrue(ModuleVersion.isAtLeast("2.1.18-r2", ModuleContract.MIN_VERSION))
        assertTrue(ModuleVersion.isAtLeast("2.2.0", ModuleContract.MIN_VERSION))
        assertFalse(ModuleVersion.isAtLeast("2.1.17-r9", ModuleContract.MIN_VERSION))
        assertFalse(ModuleVersion.isAtLeast("2.1.18", ModuleContract.MIN_VERSION))
        assertFalse(ModuleVersion.isAtLeast("2.0.45-r3", ModuleContract.MIN_VERSION))
    }
}
