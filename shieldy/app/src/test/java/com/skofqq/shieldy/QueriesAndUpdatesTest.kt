package com.skofqq.shieldy

import com.skofqq.shieldy.data.AppUpdater
import com.skofqq.shieldy.data.LogLevel
import com.skofqq.shieldy.data.QueryLogLine
import com.skofqq.shieldy.data.StatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueriesAndUpdatesTest {

    @Test
    fun queryLogLineIsSplitIntoNameTypeAndResult() {
        val line = QueryLogLine.parse("[2026-11-16 20:00:01]\t127.0.0.1\texample.com\tA\tPASS\t12ms\tcloudflare")
        assertEquals("2026-11-16 20:00:01", line.timestamp)
        assertEquals("PASS", line.levelText)
        assertEquals(LogLevel.INFO, line.level)
        assertEquals("example.com  A  12ms  cloudflare", line.message)
    }

    @Test
    fun blockedAndFailedQueriesAreColouredByResult() {
        assertEquals(LogLevel.WARNING, QueryLogLine.parse("[t]\t127.0.0.1\tads.example\tA\tREJECT\t0ms\t-").level)
        assertEquals(LogLevel.ERROR, QueryLogLine.parse("[t]\t127.0.0.1\tx.example\tAAAA\tSERVER_TIMEOUT\t5000ms\tquad9").level)
    }

    @Test
    fun anythingElseFallsBackToAPlainLine() {
        val line = QueryLogLine.parse("not a query line")
        assertNull(line.levelText)
        assertEquals("not a query line", line.raw)
    }

    @Test
    fun releaseJsonGivesTheSignedApk() {
        val body = """
            {"tag_name":"2.6","html_url":"https://github.com/skofqq/DNSCrypt-Android-Magisk/releases/tag/2.6",
             "assets":[
               {"name":"Shieldy-Magisk-2.6.zip","browser_download_url":"https://example.org/m.zip","size":1},
               {"name":"Shieldy-2.6-unsigned.apk","browser_download_url":"https://example.org/u.apk","size":2},
               {"name":"Shieldy-2.6.apk","browser_download_url":"https://example.org/s.apk","size":3}
             ]}
        """.trimIndent()
        val release = AppUpdater.parseRelease(body)!!
        assertEquals("2.6", release.version)
        assertEquals("https://example.org/s.apk", release.apkUrl)
        assertEquals(3L, release.size)
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(AppUpdater.parseRelease("""{"tag_name":"2.6","assets":[]}"""))
        assertNull(AppUpdater.parseRelease("""{"tag_name":"2.6","assets":[{"name":"Shieldy-2.6.apk","browser_download_url":"http://x/a.apk"}]}"""))
    }

    @Test
    fun statusJsonCarriesProfileAndQueryLog() {
        val status = StatusParser.parse(
            """{"running":true,"pid":1,"version":"2.6","query_log":true,"profile":"safe"}"""
        ).getOrThrow()
        assertEquals(true, status.queryLog)
        assertEquals("safe", status.profile)
        assertTrue(status.supportsProfiles)
        val old = StatusParser.parse("""{"running":true,"version":"2.5.3"}""").getOrThrow()
        assertNull(old.profile)
        assertEquals(false, old.supportsProfiles)
    }
}
