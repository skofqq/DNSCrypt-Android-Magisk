package io.github.skofqq.dnscryptmanager

import io.github.skofqq.dnscryptmanager.data.LevelFilter
import io.github.skofqq.dnscryptmanager.data.LogLevel
import io.github.skofqq.dnscryptmanager.data.LogLine
import io.github.skofqq.dnscryptmanager.ui.update.ChangelogBlock
import io.github.skofqq.dnscryptmanager.ui.update.ChangelogParser
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
