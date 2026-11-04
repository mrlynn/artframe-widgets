package com.mrlynn.artframe.widget

/** Why a widget file was turned away: every problem found, not just the first. */
class WidgetSpecException(val problems: List<String>) : Exception(problems.joinToString("; "))

/**
 * One HUD widget, read from a `.widget.json` manifest (docs/widgets/SPEC.md). The built-in
 * cards are manifests in the app's assets; people's own go in the widgets folder next to
 * hud.json. Both are parsed and drawn the same way.
 */
data class WidgetSpec(
    val id: String,
    val name: String,
    val description: String,
    val icon: String?,
    val size: Size,
    val card: Boolean,
    val weight: Float,
    val title: String?,
    val settings: List<Setting>,
    val data: Map<String, Source>,
    val needs: List<Need>,
    val layout: Map<String, Any?>,
    val builtIn: Boolean,
) {
    /** Small widgets stack in the left column, large ones share a row, wide ones get a row each. */
    enum class Size { SMALL, LARGE, WIDE }

    /**
     * The sizes a person can pick for a widget instead of its own (hud.json "widgetSizes"): a
     * tile in the left column, a card sharing the row, a panel with twice a card's share, or a
     * banner across the whole row.
     */
    enum class Shape(val size: Size, val weight: Float) {
        TILE(Size.SMALL, 1f), CARD(Size.LARGE, 1f), PANEL(Size.LARGE, 2f), BANNER(Size.WIDE, 1f);

        companion object {
            fun of(name: String?): Shape? = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
        }
    }

    /** This widget drawn at [shape], or as its file says when null. */
    fun resized(shape: Shape?): WidgetSpec = if (shape == null) this else copy(size = shape.size, weight = shape.weight)

    /** A [secret] setting (an API key) only ever reaches a source with a fixed site; see [Source.host]. */
    data class Setting(val key: String, val type: String, val default: Any?, val secret: Boolean = false)

    /** Where a widget's data comes from: one of the built-in providers, or an HTTP endpoint. */
    data class Source(
        val kind: String, // "http" or a built-in provider's name
        val url: String?,
        /**
         * The one site an HTTP source may contact, written out in its url. Null for a url that's
         * entirely a setting (`{{ settings.feed }}`): the person picked the site, so nothing
         * else of theirs (location, keys...) goes with it.
         */
        val host: String? = null,
        val format: String,
        val refreshSeconds: Int,
        val headers: Map<String, String>,
        val loading: String,
        val error: String,
        val optional: Boolean,
    )

    /** Shown instead of the body while [unless] is false, e.g. "Add a location to hud.json". */
    data class Need(val unless: String, val message: String)

    /** The widget's settings: each setting's default, overlaid with what hud.json says. */
    fun settingsWith(saved: Map<String, Any?>?): Map<String, Any?> =
        settings.associate { it.key to it.default } + (saved ?: emptyMap()).filterKeys { k -> settings.any { it.key == k } }

    companion object {
        const val SPEC_VERSION = 1

        /** Data the app fetches itself, from hud.json's own settings (location, calendars, ...). */
        val PROVIDERS = setOf("weather", "air", "moon", "markets", "calendar", "headlines")
        /**
         * The app's own, for its built-in manifests only: `gallery` reads the art folder, and the
         * `gallery` node draws its pictures. Not in the spec, so a widget file can't use either.
         */
        private val NATIVE_PROVIDERS = setOf("gallery")
        private val NATIVE_NODES = mapOf("gallery" to setOf("items", "max", "gap"))

        private val ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private val KEY = Regex("[A-Za-z][A-Za-z0-9_]{0,39}")
        private val TOP = setOf("spec", "id", "name", "description", "icon", "size", "card", "weight", "title", "settings", "data", "needs", "layout", "author", "version", "homepage", "\$schema")
        private const val MAX_NODES = 400
        private const val MAX_DEPTH = 20
        /** HTTP sources per widget (docs/widgets/SPEC.md, Limits). Built-in providers don't count. */
        const val MAX_HTTP_SOURCES = 6
        /** The shortest refresh for a source whose url or headers use `hud.` or `settings.` values. */
        const val PERSONAL_MIN_REFRESH = 300
        /** A `hud` or `settings` reference in a template, as the Mac app's WidgetManifest finds them. */
        private val PERSONAL_REF = Regex("""\b(hud|settings)\b""")

        /** Whether [templates] put any of the person's values (HUD settings or the widget's) into a request. */
        fun usesPersonalData(templates: Iterable<String>): Boolean = templates.any { t ->
            Regex("""\{\{(.*?)\}\}""", RegexOption.DOT_MATCHES_ALL).findAll(t).any { PERSONAL_REF.containsMatchIn(it.groupValues[1]) }
        }
        /** The text before any `{{`: https and a host, then the path, query or the end. */
        private val LITERAL_URL = Regex("""(https)://([A-Za-z0-9.-]+)(:[0-9]{1,5})?([/?#][^\{]*)?""")
        /** A url that's nothing but one setting: `{{ settings.feed }}`. */
        private val SETTING_URL = Regex("""\{\{\s*settings\.([A-Za-z][A-Za-z0-9_]*)\s*\}\}""")

        /**
         * The host an HTTP url will contact, or null when the url is a single setting. Throws when the
         * site isn't fixed: a template before the path could turn `api.example.com` into
         * `api.example.com.evil.net` or `api.example.com@evil.net`.
         */
        fun hostOf(url: String): String? {
            if (SETTING_URL.matches(url.trim())) return null
            val literal = url.substringBefore("{{")
            val m = LITERAL_URL.matchEntire(literal) ?: throw IllegalArgumentException(
                // Android turns down plain http, so an http:// widget could never load.
                if (!literal.startsWith("https://")) "the url must start with https://, or be a single setting"
                else "the url's site must be written out, not built from a template"
            )
            if (literal.length < url.length && m.groupValues[4].isEmpty()) {
                throw IllegalArgumentException("put a / (or ?) after the site, before any {{ }}")
            }
            val host = m.groupValues[2].lowercase()
            HostPolicy.problem(host)?.let { throw IllegalArgumentException(it) }
            return host
        }

        private val COMMON = setOf("type", "when", "flex", "width", "height", "before")
        private val TEXT = setOf("text", "size", "font", "color", "lines", "tabular", "tracking", "opacity", "align")
        private val ATTRS = mapOf(
            "column" to setOf("children", "gap", "align"),
            "row" to setOf("children", "gap", "align"),
            "text" to TEXT,
            "label" to setOf("text", "color"),
            "clock" to TEXT - "text" - "lines" + setOf("format", "format24"),
            "list" to setOf("items", "as", "max", "item", "gap", "direction", "sortBy", "groupBy", "header", "groupGap", "empty"),
            "sparkline" to setOf("points", "baseline", "color"),
            "bar" to setOf("color", "radius"),
            "spacer" to setOf("size"),
        )
        /** Attributes holding a bare expression rather than a `{{ }}` template. */
        private val EXPRESSIONS = setOf("when", "items", "sortBy", "groupBy", "points", "baseline")

        fun allowsProvider(kind: String?, builtIn: Boolean) = kind in PROVIDERS || (builtIn && kind in NATIVE_PROVIDERS)

        fun parse(text: String, builtIn: Boolean = false): WidgetSpec =
            parse(
                (try { Json.parse(text) } catch (e: Json.ParseException) { throw WidgetSpecException(listOf("Not valid JSON: ${e.message}")) })
                    as? Map<*, *> ?: throw WidgetSpecException(listOf("A widget file is a JSON object")),
                builtIn,
            )

        fun parse(j: Map<*, *>, builtIn: Boolean = false): WidgetSpec {
            val problems = mutableListOf<String>()
            fun str(key: String) = j[key] as? String

            for (k in j.keys) if (k !in TOP && !(k as String).startsWith("x-")) problems += "Unknown key \"$k\""
            val spec = (j["spec"] as? Number)?.toInt()
            when {
                spec == null -> problems += "\"spec\" must be the spec version, $SPEC_VERSION"
                spec > SPEC_VERSION -> problems += "Needs widget spec $spec; this ArtFrame reads up to $SPEC_VERSION"
            }
            val id = str("id").orEmpty()
            if (!ID.matches(id)) problems += "\"id\" must be 1-64 lowercase letters, digits, '.', '_' or '-'"
            val name = str("name").orEmpty().ifBlank { problems += "\"name\" is required"; id }
            val size = when (str("size") ?: "small") {
                "small" -> Size.SMALL
                "large" -> Size.LARGE
                "wide" -> Size.WIDE
                else -> Size.SMALL.also { problems += "\"size\" must be small, large or wide" }
            }
            str("title")?.let { checkTemplate(it, "title", problems) }

            val settings = (j["settings"] as? List<*> ?: emptyList<Any?>()).mapIndexedNotNull { i, s ->
                val m = s as? Map<*, *> ?: return@mapIndexedNotNull null.also { problems += "settings[$i] must be an object" }
                val key = m["key"] as? String ?: ""
                if (!KEY.matches(key)) problems += "settings[$i].key must be a name like \"symbol\""
                val type = m["type"] as? String ?: "text"
                if (type !in setOf("text", "number", "toggle", "choice")) problems += "settings[$i].type must be text, number, toggle or choice"
                val secret = m["secret"] == true
                if (secret && type != "text") problems += "settings[$i]: only a text setting can be secret"
                if (secret && m["default"] != null) problems += "settings[$i] is secret, so it can't have a default"
                if (type == "choice" && (m["options"] as? List<*>).isNullOrEmpty()) problems += "settings[$i] is a choice with no options"
                Setting(key, type, m["default"], secret)
            }
            if (settings.map { it.key }.toSet().size != settings.size) problems += "Two settings share a key"

            val data = (j["data"] as? Map<*, *> ?: emptyMap<Any?, Any?>()).entries.associate { (k, v) ->
                val name = k as String
                if (!KEY.matches(name)) problems += "data \"$name\" needs a name like \"main\""
                name to source(name, v as? Map<*, *>, settings, builtIn, problems)
            }
            val httpCount = data.values.count { it.kind == "http" }
            if (httpCount > MAX_HTTP_SOURCES) problems += "At most $MAX_HTTP_SOURCES HTTP data sources; this widget has $httpCount"

            val needs = (j["needs"] as? List<*> ?: emptyList<Any?>()).mapNotNull { n ->
                val m = n as? Map<*, *>
                val unless = m?.get("unless") as? String
                val message = m?.get("message") as? String
                if (unless == null || message == null) {
                    problems += "Each of \"needs\" has \"unless\" and \"message\""
                    null
                } else {
                    checkExpr(unless, "needs.unless", problems)
                    checkTemplate(message, "needs.message", problems)
                    Need(unless, message)
                }
            }

            @Suppress("UNCHECKED_CAST")
            val layout = j["layout"] as? Map<String, Any?>
            if (layout == null) problems += "\"layout\" is required" else checkNode(layout, "layout", 0, intArrayOf(0), builtIn, problems)

            if (problems.isNotEmpty()) throw WidgetSpecException(problems)
            return WidgetSpec(
                id = id,
                name = name,
                description = str("description").orEmpty(),
                icon = str("icon"),
                size = size,
                card = j["card"] as? Boolean ?: true,
                weight = (j["weight"] as? Number)?.toFloat()?.coerceIn(0.25f, 4f) ?: 1f,
                title = str("title"),
                settings = settings,
                data = data,
                needs = needs,
                layout = layout!!,
                builtIn = builtIn,
            )
        }

        private fun source(name: String, m: Map<*, *>?, settings: List<Setting>, builtIn: Boolean, problems: MutableList<String>): Source {
            val kind = m?.get("source") as? String
            if (kind == null || (kind != "http" && !allowsProvider(kind, builtIn))) {
                problems += "data \"$name\": \"source\" must be http or one of ${PROVIDERS.sorted().joinToString()}"
            }
            val url = m?.get("url") as? String
            var host: String? = null
            var chosen = false // the url is a setting, so the person picked the site
            if (kind == "http") {
                if (url == null) problems += "data \"$name\" needs a \"url\""
                else {
                    checkTemplate(url, "data.$name.url", problems)
                    try {
                        host = hostOf(url)
                        if (host == null) {
                            chosen = true
                            val key = SETTING_URL.matchEntire(url.trim())!!.groupValues[1]
                            if (settings.none { it.key == key && it.type == "text" }) problems += "data \"$name\": the url setting \"$key\" must be a text setting"
                        }
                    } catch (e: IllegalArgumentException) {
                        problems += "data \"$name\": ${e.message}"
                    }
                }
            }
            val format = m?.get("format") as? String ?: "json"
            if (format !in setOf("json", "rss", "text")) problems += "data \"$name\": \"format\" must be json, rss or text"
            val headers = (m?.get("headers") as? Map<*, *>)?.entries?.associate { (k, v) ->
                (k as String) to (v as? String ?: "").also {
                    checkTemplate(it, "data.$name.headers", problems)
                    if (chosen && "{{" in it) problems += "data \"$name\": headers can't use {{ }} when the url is a setting"
                }
            } ?: emptyMap()
            val declared = (m?.get("refresh") as? Number)?.toInt()
            if (kind == "http" && url != null && usesPersonalData(listOf(url) + headers.values) && declared != null && declared < PERSONAL_MIN_REFRESH) {
                problems += "data \"$name\": \"refresh\" must be at least $PERSONAL_MIN_REFRESH when the url or headers use hud. or settings. values"
            }
            return Source(
                kind = kind ?: "http",
                url = url,
                host = host,
                format = format,
                refreshSeconds = declared?.coerceIn(60, 86_400) ?: 900,
                headers = headers,
                loading = m?.get("loading") as? String ?: "Loading…",
                error = m?.get("error") as? String ?: "Couldn't load",
                optional = m?.get("optional") as? Boolean ?: false,
            )
        }

        private fun checkNode(n: Map<String, Any?>, path: String, depth: Int, count: IntArray, builtIn: Boolean, problems: MutableList<String>) {
            if (++count[0] > MAX_NODES) return run { if (count[0] == MAX_NODES + 1) problems += "More than $MAX_NODES layout nodes" }
            if (depth > MAX_DEPTH) return run { problems += "$path: nested more than $MAX_DEPTH deep" }
            val type = n["type"] as? String
            val allowed = ATTRS[type] ?: NATIVE_NODES[type]?.takeIf { builtIn } ?: return run { problems += "$path: unknown type \"$type\"" }
            for ((k, v) in n) {
                when {
                    k !in COMMON && k !in allowed -> problems += "$path: \"$type\" has no \"$k\""
                    k in EXPRESSIONS -> if (v is String) checkExpr(v, "$path.$k", problems) else problems += "$path.$k must be an expression"
                    v is String -> checkTemplate(v, "$path.$k", problems)
                }
            }
            when (type) {
                "column", "row" -> (n["children"] as? List<*>)?.forEachIndexed { i, c ->
                    @Suppress("UNCHECKED_CAST")
                    (c as? Map<String, Any?>)?.let { checkNode(it, "$path.children[$i]", depth + 1, count, builtIn, problems) }
                        ?: run { problems += "$path.children[$i] must be an object" }
                } ?: run { problems += "$path: a $type needs \"children\"" }
                "list" -> {
                    if (n["items"] == null) problems += "$path: a list needs \"items\""
                    @Suppress("UNCHECKED_CAST")
                    (n["item"] as? Map<String, Any?>)?.let { checkNode(it, "$path.item", depth + 1, count, builtIn, problems) }
                        ?: run { problems += "$path: a list needs an \"item\"" }
                    @Suppress("UNCHECKED_CAST")
                    (n["header"] as? Map<String, Any?>)?.let { checkNode(it, "$path.header", depth + 1, count, builtIn, problems) }
                }
                "text", "label" -> if (n["text"] == null) problems += "$path: a $type needs \"text\""
                "sparkline" -> if (n["points"] == null) problems += "$path: a sparkline needs \"points\""
            }
        }

        private fun checkExpr(e: String, where: String, problems: MutableList<String>) {
            try { Exprs.parse(e) } catch (x: ExprException) { problems += "$where: ${x.message}" }
        }

        private fun checkTemplate(t: String, where: String, problems: MutableList<String>) {
            try { Template.check(t) } catch (x: ExprException) { problems += "$where: ${x.message}" }
        }
    }
}
