package com.mrlynn.artframe.widget

/**
 * Review hints for a widget's own (static) text: phone numbers, web addresses and "scan" or "call"
 * wording. A widget mustn't ask viewers to scan, call or visit anything (docs/widgets/SPEC.md,
 * "Text is just text"), but these are warnings for a person to read, not errors: "Call of Duty"
 * or a "1,234,567,890" count are fine. The gallery checker reports them; the TV doesn't run this.
 */
object TextLint {
    private val HOLE = Regex("""\{\{((?:(?!\}\}).)*)\}\}""", RegexOption.DOT_MATCHES_ALL)
    private val PHONE = Regex("""(?<![\w.])\+?\(?\d{1,4}\)?[\s.-]?\(?\d{2,4}\)?[\s.-]\d{3,4}[\s.-]?\d{3,4}(?![\w.])""")
    private val URL = Regex(
        """(?i)\b(?:https?://|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|io|ly|co|me|app|xyz|info|biz|link|site|online|shop|gg|tv|to|us|uk)\b(?:/\S*)?""",
    )
    private val ACTION = Regex("""(?i)\b(scan(?:ning|ned)?|qr\s*code|call(?:ing)?|dial|text\s+us)\b""")

    /** Warnings for the static text in a parsed widget file ([Json.parse] output). */
    fun warnings(j: Map<*, *>): List<String> {
        val out = mutableListOf<String>()
        fun check(where: String, t: String?) {
            if (t.isNullOrBlank()) return
            val text = HOLE.replace(t, " ")
            PHONE.find(text)?.let { out += "$where: looks like a phone number (\"${it.value.trim()}\"); widgets mustn't ask viewers to call anything" }
            URL.find(text)?.let { out += "$where: looks like a web address (\"${it.value}\"); widgets mustn't ask viewers to visit anything" }
            ACTION.find(text)?.let { out += "$where: says \"${it.value}\"; widgets mustn't ask viewers to scan or call anything" }
        }
        check("name", j["name"] as? String)
        check("description", j["description"] as? String)
        check("title", j["title"] as? String)
        (j["needs"] as? List<*>)?.forEachIndexed { i, n -> check("needs[$i].message", (n as? Map<*, *>)?.get("message") as? String) }
        (j["data"] as? Map<*, *>)?.forEach { (k, v) ->
            val m = v as? Map<*, *>
            check("data.$k.loading", m?.get("loading") as? String)
            check("data.$k.error", m?.get("error") as? String)
        }
        fun node(n: Any?, path: String) {
            val m = n as? Map<*, *> ?: return
            for (key in listOf("text", "empty", "format", "format24")) check("$path.$key", m[key] as? String)
            (m["children"] as? List<*>)?.forEachIndexed { i, c -> node(c, "$path.children[$i]") }
            node(m["item"], "$path.item")
            node(m["header"], "$path.header")
        }
        node(j["layout"], "layout")
        return out.distinct()
    }
}
