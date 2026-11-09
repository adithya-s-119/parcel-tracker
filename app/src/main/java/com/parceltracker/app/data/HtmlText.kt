package com.parceltracker.app.data

import java.util.Locale

/** Small, dependency-free HTML-to-text helper so the parser is testable on the JVM. */
object HtmlText {

    private val scriptStyle = Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>")
    private val breaks = Regex("(?i)<br\\s*/?>|</(p|div|tr|li|h[1-6]|table)>")
    private val tags = Regex("(?s)<[^>]+>")
    private val spaces = Regex("[\\t\\x0B\\f\\r]+")
    private val multiSpace = Regex(" {2,}")
    private val multiNewline = Regex("\\n{3,}")

    fun looksLikeHtml(value: String): Boolean =
        Regex("(?i)<(html|body|div|p|table|br|span|td)\\b").containsMatchIn(value)

    fun toPlain(html: String): String {
        var text = scriptStyle.replace(html, " ")
        text = breaks.replace(text, "\n")
        text = tags.replace(text, " ")
        text = decodeEntities(text)
        text = spaces.replace(text, " ")
        text = text.replace(" *\n *".toRegex(), "\n")
        text = multiSpace.replace(text, " ")
        text = multiNewline.replace(text, "\n\n")
        return text.trim()
    }

    private fun decodeEntities(value: String): String {
        var text = value
        text = Regex("&#x([0-9a-fA-F]+);").replace(text) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { codePointToString(it) } ?: ""
        }
        text = Regex("&#(\\d+);").replace(text) { match ->
            match.groupValues[1].toIntOrNull()?.let { codePointToString(it) } ?: ""
        }
        val named = mapOf(
            "&nbsp;" to " ",
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&#39;" to "'",
            "&apos;" to "'",
            "&rsquo;" to "'",
            "&lsquo;" to "'",
            "&ldquo;" to "\"",
            "&rdquo;" to "\"",
            "&ndash;" to "–",
            "&mdash;" to "—",
            "&hellip;" to "…",
            "&copy;" to "©",
            "&reg;" to "®",
            "&trade;" to "™",
            "&euro;" to "€",
            "&pound;" to "£",
            "&rupee;" to "₹",
        )
        for ((entity, replacement) in named) {
            text = text.replace(entity, replacement, ignoreCase = true)
        }
        return text
    }

    private fun codePointToString(codePoint: Int): String =
        if (Character.isValidCodePoint(codePoint)) {
            String(Character.toChars(codePoint))
        } else {
            ""
        }

    fun normalizeSpace(value: String): String = value
        .replace('\u00A0', ' ')
        .replace(spaces, " ")
        .replace(multiSpace, " ")
        .trim()

    fun titleCase(value: String): String =
        value.lowercase(Locale.getDefault()).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
}
