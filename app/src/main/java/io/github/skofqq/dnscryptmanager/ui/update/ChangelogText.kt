package io.github.skofqq.dnscryptmanager.ui.update

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** One rendered piece of CHANGELOG.md. */
sealed interface ChangelogBlock {
    data class Heading(val text: String) : ChangelogBlock
    data class Bullet(val text: String) : ChangelogBlock
    data class Paragraph(val text: String) : ChangelogBlock
}

/**
 * The little Markdown CHANGELOG.md uses: `#` headings, `*` / `-` bullets, paragraphs
 * hard-wrapped at ~70 columns. Wrapped lines are joined back so the text reflows.
 */
object ChangelogParser {

    private val BULLET = Regex("""^[*-]\s+""")

    fun parse(markdown: String): List<ChangelogBlock> {
        val blocks = ArrayList<ChangelogBlock>()
        var current: StringBuilder? = null
        var bullet = false

        fun flush() {
            val text = current?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                blocks.add(if (bullet) ChangelogBlock.Bullet(text) else ChangelogBlock.Paragraph(text))
            }
            current = null
            bullet = false
        }

        markdown.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("#") -> {
                    flush()
                    blocks.add(ChangelogBlock.Heading(line.trimStart('#').trim()))
                }
                BULLET.containsMatchIn(line) -> {
                    flush()
                    bullet = true
                    current = StringBuilder(line.replaceFirst(BULLET, ""))
                }
                else -> {
                    val builder = current ?: StringBuilder().also { current = it }
                    if (builder.isNotEmpty()) builder.append(' ')
                    builder.append(line)
                }
            }
        }
        flush()
        return blocks
    }
}

/** Headings bold in [strong], bullets with a dot, `code` in monospace on [codeBackground]. */
fun renderChangelog(markdown: String, strong: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    ChangelogParser.parse(markdown).forEachIndexed { index, block ->
        if (index > 0) append(if (block is ChangelogBlock.Bullet) "\n" else "\n\n")
        when (block) {
            is ChangelogBlock.Heading -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) {
                append(block.text)
            }
            is ChangelogBlock.Bullet -> {
                append("•  ")
                appendInline(block.text, codeBackground)
            }
            is ChangelogBlock.Paragraph -> appendInline(block.text, codeBackground)
        }
    }
}

private fun AnnotatedString.Builder.appendInline(text: String, codeBackground: Color) {
    text.split('`').forEachIndexed { i, part ->
        if (i % 2 == 1) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(part) }
        } else {
            append(part)
        }
    }
}
