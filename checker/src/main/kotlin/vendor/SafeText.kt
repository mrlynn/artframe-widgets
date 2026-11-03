package com.mrlynn.artframe.widget

/**
 * Text is shown, never acted on (docs/widgets/SPEC.md, "Text is just text"). What a template puts
 * on screen, much of it straight from a widget's APIs, is cleaned here: control characters and
 * bidi overrides (which can make "moc.evil" read as "evil.com") are dropped, and it's capped at
 * [MAX_TEXT] characters, [MAX_ITEM] inside a list item.
 */
object SafeText {
    const val MAX_TEXT = 500
    const val MAX_ITEM = 200

    /** Bidi embeddings, overrides and isolates, marks, and invisible characters that hide text. */
    private val INVISIBLE = Regex("[\\u061C\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF\\uFFF9-\\uFFFB]")
    /** C0 and C1 controls other than newline and tab. */
    private val CONTROL = Regex("[\\u0000-\\u0008\\u000B-\\u001F\\u007F-\\u009F]")

    /** [s] without control or bidi characters; tabs and carriage returns become spaces. */
    fun clean(s: String): String {
        if (s.isEmpty()) return s
        return s.replace("\r\n", "\n").replace('\r', ' ').replace('\t', ' ')
            .replace(CONTROL, "").replace(INVISIBLE, "")
    }

    /** [s] cut to at most [max] characters, ending in "…" when cut, never splitting a surrogate pair. */
    fun cap(s: String, max: Int): String {
        if (s.length <= max) return s
        var end = max - 1
        if (end > 0 && Character.isHighSurrogate(s[end - 1])) end--
        return s.substring(0, end) + "…"
    }

    fun forDisplay(s: String, max: Int = MAX_TEXT): String = cap(clean(s), max)
}
