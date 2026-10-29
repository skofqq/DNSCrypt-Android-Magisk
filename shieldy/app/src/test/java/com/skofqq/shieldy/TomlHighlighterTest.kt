package com.skofqq.shieldy

import com.skofqq.shieldy.ui.config.TomlKind
import com.skofqq.shieldy.ui.config.tomlTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class TomlHighlighterTest {

    private fun tokens(text: String): List<Pair<TomlKind, String>> =
        tomlTokens(text).map { it.kind to text.substring(it.start, it.end) }

    @Test
    fun `comment line is one comment token`() {
        assertEquals(listOf(TomlKind.Comment to "## List of servers"), tokens("  ## List of servers"))
    }

    @Test
    fun `table and array-of-tables headers`() {
        assertEquals(
            listOf(TomlKind.Header to "[sources]", TomlKind.Header to "[[static.x]]"),
            tokens("[sources]\n[[static.x]]"),
        )
    }

    @Test
    fun `key with string number and bool values`() {
        assertEquals(
            listOf(
                TomlKind.Key to "listen_addresses", TomlKind.Str to "'127.0.0.1:5354'",
                TomlKind.Key to "max_clients", TomlKind.Number to "250",
                TomlKind.Key to "ipv6_servers", TomlKind.Bool to "false",
            ),
            tokens("listen_addresses = ['127.0.0.1:5354']\nmax_clients = 250\nipv6_servers = false"),
        )
    }

    @Test
    fun `inline comment after a value`() {
        assertEquals(
            listOf(TomlKind.Key to "timeout", TomlKind.Number to "5000", TomlKind.Comment to "# ms"),
            tokens("timeout = 5000  # ms"),
        )
    }

    @Test
    fun `hash inside a string is not a comment`() {
        assertEquals(
            listOf(TomlKind.Key to "url", TomlKind.Str to "\"https://a.b/#frag\"", TomlKind.Comment to "# real"),
            tokens("url = \"https://a.b/#frag\" # real"),
        )
    }

    @Test
    fun `multi-line array stays in value mode and keys resume after it`() {
        assertEquals(
            listOf(
                TomlKind.Key to "urls", TomlKind.Str to "'a'", TomlKind.Comment to "# first",
                TomlKind.Str to "'b'", TomlKind.Key to "next", TomlKind.Bool to "true",
            ),
            tokens("urls = [\n  'a', # first\n  'b',\n]\nnext = true"),
        )
    }

    @Test
    fun `triple-quoted string spans lines`() {
        assertEquals(
            listOf(TomlKind.Key to "s", TomlKind.Str to "\"\"\"one\ntwo\"\"\"", TomlKind.Key to "k", TomlKind.Number to "1"),
            tokens("s = \"\"\"one\ntwo\"\"\"\nk = 1"),
        )
    }
}
