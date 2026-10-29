package com.skofqq.shieldy.ui.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

enum class TomlKind { Comment, Header, Key, Str, Number, Bool }

/** A highlighted range of the text, [start] inclusive, [end] exclusive. */
data class TomlToken(val kind: TomlKind, val start: Int, val end: Int)

/**
 * A single linear pass over a TOML document: no regexes, so a 30 KB config costs a few
 * milliseconds. It is a highlighter, not a parser: broken input still gets coloured
 * sensibly up to the end of the line.
 */
fun tomlTokens(text: String): List<TomlToken> {
    val out = ArrayList<TomlToken>()
    val n = text.length
    var i = 0
    var lineStart = true
    var depth = 0 // open [ and { inside a value, so multi-line arrays stay in value mode

    fun lineEnd(from: Int): Int = text.indexOf('\n', from).let { if (it < 0) n else it }

    fun comment(from: Int): Int {
        val end = lineEnd(from)
        out.add(TomlToken(TomlKind.Comment, from, end))
        return end
    }

    fun string(from: Int): Int {
        val q = text[from]
        if (text.startsWith("$q$q$q", from)) {
            val close = text.indexOf("$q$q$q", from + 3)
            val end = if (close < 0) n else close + 3
            out.add(TomlToken(TomlKind.Str, from, end))
            return end
        }
        var j = from + 1
        while (j < n && text[j] != q && text[j] != '\n') {
            if (q == '"' && text[j] == '\\') j++
            j++
        }
        val end = if (j < n && text[j] == q) j + 1 else minOf(j, n)
        out.add(TomlToken(TomlKind.Str, from, end))
        return end
    }

    while (i < n) {
        val c = text[i]
        if (c == '\n') {
            if (depth == 0) lineStart = true
            i++
            continue
        }
        if (c == ' ' || c == '\t' || c == '\r') {
            i++
            continue
        }
        if (c == '#') {
            i = comment(i)
            continue
        }
        if (lineStart) {
            lineStart = false
            if (c == '[') {
                // [table] or [[array.of.tables]]: up to the last ']' before a comment.
                var j = i
                var last = -1
                while (j < n && text[j] != '\n' && text[j] != '#') {
                    if (text[j] == ']') last = j
                    j++
                }
                val end = if (last >= 0) last + 1 else j
                out.add(TomlToken(TomlKind.Header, i, end))
                i = end
                continue
            }
            // A key: everything up to '=', quoted parts included.
            var j = i
            var quote = 0.toChar()
            while (j < n && text[j] != '\n') {
                val ch = text[j]
                if (quote != 0.toChar()) {
                    if (ch == quote) quote = 0.toChar()
                } else if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (ch == '=' || ch == '#') {
                    break
                }
                j++
            }
            var keyEnd = j
            while (keyEnd > i && text[keyEnd - 1].isWhitespace()) keyEnd--
            if (keyEnd > i) out.add(TomlToken(TomlKind.Key, i, keyEnd))
            i = if (j < n && text[j] == '=') j + 1 else j
            continue
        }
        when {
            c == '"' || c == '\'' -> i = string(i)
            c == '[' || c == '{' -> { depth++; i++ }
            c == ']' || c == '}' -> { if (depth > 0) depth--; i++ }
            c.isDigit() || ((c == '+' || c == '-') && i + 1 < n && text[i + 1].isDigit()) -> {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] in "_.:+-")) j++
                out.add(TomlToken(TomlKind.Number, i, j))
                i = j
            }
            c.isLetter() -> {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '-')) j++
                when (text.substring(i, j)) {
                    "true", "false" -> out.add(TomlToken(TomlKind.Bool, i, j))
                    "inf", "nan" -> out.add(TomlToken(TomlKind.Number, i, j))
                    // A bare word inside an inline table is a key: { a = 1 }
                    else -> if (depth > 0) out.add(TomlToken(TomlKind.Key, i, j))
                }
                i = j
            }
            else -> i++
        }
    }
    return out
}

@Immutable
data class TomlColors(
    val comment: Color,
    val header: Color,
    val key: Color,
    val string: Color,
    val number: Color,
    val bool: Color,
)

/**
 * Colours the editor text. The last result is kept, so recompositions that do not
 * change the text (cursor moves, search jumps) do not re-tokenize.
 */
class TomlVisualTransformation(private val colors: TomlColors) : VisualTransformation {
    private var lastText: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val cached = lastResult
        val styled = if (cached != null && lastText == raw) cached else highlight(raw).also {
            lastText = raw
            lastResult = it
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private fun highlight(raw: String): AnnotatedString {
        val builder = AnnotatedString.Builder(raw)
        for (token in tomlTokens(raw)) {
            val color = when (token.kind) {
                TomlKind.Comment -> colors.comment
                TomlKind.Header -> colors.header
                TomlKind.Key -> colors.key
                TomlKind.Str -> colors.string
                TomlKind.Number -> colors.number
                TomlKind.Bool -> colors.bool
            }
            builder.addStyle(SpanStyle(color = color), token.start, token.end)
        }
        return builder.toAnnotatedString()
    }

    // Cached state is not part of identity: two instances with the same colours are equal.
    override fun equals(other: Any?): Boolean = other is TomlVisualTransformation && other.colors == colors
    override fun hashCode(): Int = colors.hashCode()
}
