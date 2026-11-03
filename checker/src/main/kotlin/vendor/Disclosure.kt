package com.mrlynn.artframe.widget

/**
 * What of the person's each fixed site gets, in the Mac app's words ("your city", "your API key",
 * "its “Repository” setting"), for the gallery index's "sends". Mirrors WidgetManifest.site(...)
 * in remote/Sources/WidgetManifest.swift; DisclosureTest and the Mac's tests share the same cases,
 * and the Mac refuses a gallery file that sends more than its listing says. The TV doesn't run this.
 */
object Disclosure {
    private val PERSONAL = mapOf(
        "location" to "your city",
        "calendars" to "your calendar names",
        "markets" to "your market symbols",
        "feeds" to "your feed names",
    )
    private val HUD_REF = Regex("""\bhud\s*(?:\.\s*([A-Za-z]+)|\[\s*"([A-Za-z]+)"\s*\])?""")
    private val SETTING_REF = Regex("""\bsettings\s*(?:\.\s*([A-Za-z][A-Za-z0-9_]*)|\[\s*"([A-Za-z][A-Za-z0-9_]*)"\s*\])?""")

    data class Site(val sends: List<String>, val keyInUrl: Boolean)

    /** Host to what it gets, merged across a widget's HTTP sources; [j] is the parsed widget file. */
    fun sites(j: Map<*, *>): Map<String, Site> {
        val settings = (j["settings"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
        val out = sortedMapOf<String, Site>()
        for ((_, v) in (j["data"] as? Map<*, *>).orEmpty().entries.sortedBy { it.key.toString() }) {
            val m = v as? Map<*, *> ?: continue
            if (m["source"] != "http") continue
            val url = (m["url"] as? String)?.trim() ?: continue
            val host = WidgetSpec.hostOf(url) ?: continue
            val headers = (m["headers"] as? Map<*, *>).orEmpty().entries.sortedBy { it.key.toString() }.mapNotNull { it.value as? String }
            val sends = mutableListOf<String>()
            var keyInUrl = false
            for ((i, text) in (listOf(url) + headers).withIndex()) {
                for (g in HUD_REF.findAll(text)) {
                    val field = g.groups[1]?.value ?: g.groups[2]?.value
                    if (field != null) PERSONAL[field]?.let { sends += it } else sends += "your HUD settings"
                }
                for (g in SETTING_REF.findAll(text)) {
                    val key = g.groups[1]?.value ?: g.groups[2]?.value
                    val s = settings.firstOrNull { it["key"] == key }
                    if (key != null && s != null) {
                        val label = s["label"] as? String ?: key
                        val secret = s["secret"] == true
                        if (secret && i == 0) keyInUrl = true
                        sends += if (secret) "your $label" else "its “$label” setting"
                    } else {
                        sends += "its settings"
                    }
                }
            }
            val before = out[host]
            out[host] = Site(((before?.sends ?: emptyList()) + sends).distinct(), (before?.keyInUrl ?: false) || keyInUrl)
        }
        return out
    }

    /** The Mac's sentence for one site: "Sends your city to api.example.com." */
    fun sentence(host: String, sends: List<String>): String =
        if (sends.isEmpty()) "Contacts $host, sending nothing about you." else "Sends ${list(sends)} to $host."

    fun list(items: List<String>): String =
        if (items.size <= 1) items.joinToString("") else items.dropLast(1).joinToString(", ") + " and " + items.last()
}
