package com.mrlynn.artframe.widget

/**
 * Which hosts a widget may name (docs/widgets/SPEC.md, "Which sites a widget contacts"): DNS names
 * only, so a widget can't reach the local network or hide where it goes behind a link shortener.
 * The Mac app mirrors this in WidgetManifest.swift (HostPolicy); keep the two in step.
 */
object HostPolicy {
    /** Link shorteners and redirectors. Hardcoded; matched on the host and any subdomain of it. */
    val SHORTENERS = setOf(
        "bit.ly", "bitly.com", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "rebrand.ly",
        "cutt.ly", "shorturl.at", "rb.gy", "t.ly", "tiny.cc", "lnkd.in", "s.id", "v.gd", "bl.ink",
        "short.io", "shorte.st", "adf.ly", "soo.gd", "clck.ru", "qr.ae", "trib.al", "dlvr.it", "x.co",
        "po.st", "mcaf.ee", "tr.im", "u.to", "y2u.be", "youtu.be", "amzn.to", "fb.me", "linktr.ee",
    )

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    /** Every label numeric (with an optional trailing dot): "127.1", "2130706433", "0x7f.1". */
    private val NUMERIC = Regex("""((0x[0-9a-f]+|\d+)\.?)+""")

    /** Why [host] isn't allowed, or null when it is. [host] is lowercase, without a port. */
    fun problem(host: String): String? {
        val h = host.lowercase().trimEnd('.')
        return when {
            h.isEmpty() -> "the url needs a site"
            h.contains(':') || h.startsWith("[") -> "the site must be a name, not an IP address"
            IPV4.matches(h) || NUMERIC.matches(h) -> "the site must be a name, not an IP address"
            h == "localhost" || h.endsWith(".localhost") -> "the site can't be localhost"
            h == "local" || h.endsWith(".local") -> "the site can't be a .local name on your network"
            '.' !in h -> "the site must be a full name, like api.example.com"
            SHORTENERS.any { h == it || h.endsWith(".$it") } -> "the site can't be a link shortener ($h)"
            else -> null
        }
    }
}
