package com.mrlynn.artframe.widget

import java.math.BigDecimal
import java.math.MathContext
import java.net.URLEncoder
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The widget spec's expression language (docs/widgets/SPEC.md, "Expressions"): paths into
 * the data, literals, comparisons, `&&` `||` `!`, arithmetic, `a ? b : c`, and filters
 * piped on the end (`data.temp | round`). Nothing here can reach outside the values it's
 * given: no calls, no assignment, no loops.
 */
class ExprException(message: String) : Exception(message)

/** The names an expression can read. A list item's scope sees its parent's names too. */
class Scope(private val vars: Map<String, Any?>, private val parent: Scope? = null, now: ZonedDateTime? = null) {
    val now: ZonedDateTime = now ?: parent?.now ?: ZonedDateTime.now()
    operator fun get(name: String): Any? = if (vars.containsKey(name)) vars[name] else parent?.get(name)
    fun with(vararg pairs: Pair<String, Any?>) = Scope(mapOf(*pairs), this)
}

fun interface Expr {
    fun eval(s: Scope): Any?
}

object Exprs {
    private val cache = HashMap<String, Expr>()

    /**
     * A list node's `sortBy`: [items] in ascending order of [by], worked out for each item as
     * [name]. Items it gives nothing for go last; ties keep their order.
     */
    fun sortBy(items: List<*>, by: String?, name: String, scope: Scope): List<*> {
        if (by == null) return items
        return items.map { it to eval(by, scope.with(name to it)) }.sortedWith { (_, a), (_, b) ->
            when {
                a == null && b == null -> 0
                a == null -> 1
                b == null -> -1
                else -> Values.compare(a, b) ?: 0
            }
        }.map { it.first }
    }

    /** Parses (and caches) an expression; throws [ExprException] if it isn't valid. */
    fun parse(src: String): Expr = synchronized(cache) {
        cache[src] ?: Parser(src).parseAll().also {
            if (cache.size > 4000) cache.clear()
            cache[src] = it
        }
    }

    fun eval(src: String, s: Scope): Any? = parse(src).eval(s)
    fun test(src: String, s: Scope): Boolean = Values.truthy(eval(src, s))
}

/** Strings with `{{ expression }}` holes. */
object Template {
    // Every brace escaped: Android's ICU regex rejects a bare "}" that desktop Java accepts.
    private val HOLE = Regex("""\{\{((?:(?!\}\}).)*)\}\}""", RegexOption.DOT_MATCHES_ALL)

    /** Each hole's value is [SafeText.clean]ed: it often comes straight from an API. */
    fun render(t: String, s: Scope): String = HOLE.replace(t) { SafeText.clean(Values.text(Exprs.eval(it.groupValues[1], s))) }

    /** A template that is exactly one hole gives that value as is (a number, list...); anything else renders to text. */
    fun value(t: Any?, s: Scope): Any? {
        if (t !is String) return t
        val whole = HOLE.matchEntire(t.trim())
        return if (whole != null) Exprs.eval(whole.groupValues[1], s) else render(t, s)
    }

    /** Throws [ExprException] for the first hole that doesn't parse. */
    fun check(t: String) = HOLE.findAll(t).forEach { Exprs.parse(it.groupValues[1]) }
}

object Values {
    fun truthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0 && !v.toDouble().isNaN()
        is String -> v.isNotEmpty()
        is Collection<*> -> v.isNotEmpty()
        is Map<*, *> -> v.isNotEmpty()
        else -> true
    }

    fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        is Boolean -> if (v) 1.0 else 0.0
        else -> null
    }

    fun text(v: Any?): String = when (v) {
        null -> ""
        is Double -> if (v.isNaN() || v.isInfinite()) "" else if (v == Math.floor(v) && abs(v) < 1e15) v.toLong().toString()
            else BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
        is Float -> text(v.toDouble())
        is Collection<*> -> v.joinToString(", ") { text(it) }
        is Map<*, *> -> ""
        else -> v.toString()
    }

    fun eq(a: Any?, b: Any?): Boolean {
        if (a == null || b == null) return a == b
        val x = num(a)
        val y = num(b)
        if (a is Number || b is Number) return x != null && y != null && x == y
        return text(a) == text(b)
    }

    fun compare(a: Any?, b: Any?): Int? {
        if (a == null || b == null) return null
        val x = num(a)
        val y = num(b)
        return if (x != null && y != null) x.compareTo(y) else text(a).compareTo(text(b))
    }

    fun member(obj: Any?, key: Any?): Any? = when (obj) {
        is Map<*, *> -> obj[text(key)]
        is List<*> -> if (key == "length") obj.size else num(key)?.toInt()?.let { obj.getOrNull(it) }
        is String -> if (key == "length") obj.length else null
        else -> null
    }
}

private class Parser(private val src: String) {
    private enum class K { NUM, STR, ID, OP, END }
    private data class Tok(val k: K, val text: String, val num: Double = 0.0)

    private val toks = lex()
    private var p = 0

    private fun fail(msg: String): Nothing = throw ExprException("$msg in \"${src.trim()}\"")

    private fun lex(): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        val ops = listOf("&&", "||", "==", "!=", "<=", ">=", "<", ">", "!", "+", "-", "*", "/", "%", "?", ":", "|", ".", "[", "]", "(", ")")
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() -> {
                    val start = i
                    while (i < src.length && src[i].isDigit()) i++
                    if (i + 1 < src.length && src[i] == '.' && src[i + 1].isDigit()) {
                        i++
                        while (i < src.length && src[i].isDigit()) i++
                    }
                    out += Tok(K.NUM, src.substring(start, i), src.substring(start, i).toDouble())
                }
                c == '\'' || c == '"' -> {
                    val b = StringBuilder()
                    i++
                    while (i < src.length && src[i] != c) {
                        if (src[i] == '\\' && i + 1 < src.length) i++
                        b.append(src[i++])
                    }
                    if (i >= src.length) fail("Unterminated string")
                    i++
                    out += Tok(K.STR, b.toString())
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '$')) i++
                    out += Tok(K.ID, src.substring(start, i))
                }
                else -> {
                    val op = ops.firstOrNull { src.startsWith(it, i) } ?: fail("Unexpected '$c'")
                    out += Tok(K.OP, op)
                    i += op.length
                }
            }
        }
        out += Tok(K.END, "")
        return out
    }

    private fun peek(op: String) = toks[p].k == K.OP && toks[p].text == op
    private fun take(op: String) = peek(op).also { if (it) p++ }
    private fun expect(op: String) { if (!take(op)) fail("Expected '$op'") }

    fun parseAll(): Expr {
        if (toks.size == 1) fail("Empty expression")
        val e = pipe()
        if (toks[p].k != K.END) fail("Unexpected '${toks[p].text}'")
        return e
    }

    private fun pipe(): Expr {
        var e = ternary()
        while (take("|")) {
            val name = toks[p].takeIf { it.k == K.ID }?.text ?: fail("Expected a filter name")
            p++
            val f = Filters.all[name] ?: fail("Unknown filter '$name'")
            val args = mutableListOf<Expr>()
            while (take(":")) args += unary()
            val inner = e
            e = Expr { s -> f(inner.eval(s), args.map { it.eval(s) }, s) }
        }
        return e
    }

    private fun ternary(): Expr {
        val c = or()
        if (!take("?")) return c
        val a = ternary()
        expect(":")
        val b = ternary()
        return Expr { s -> if (Values.truthy(c.eval(s))) a.eval(s) else b.eval(s) }
    }

    private fun or(): Expr {
        var l = and()
        while (take("||")) {
            val a = l
            val b = and()
            l = Expr { s -> a.eval(s).let { if (Values.truthy(it)) it else b.eval(s) } }
        }
        return l
    }

    private fun and(): Expr {
        var l = cmp()
        while (take("&&")) {
            val a = l
            val b = cmp()
            l = Expr { s -> a.eval(s).let { if (!Values.truthy(it)) it else b.eval(s) } }
        }
        return l
    }

    private fun cmp(): Expr {
        val l = add()
        val op = listOf("==", "!=", "<=", ">=", "<", ">").firstOrNull { peek(it) } ?: return l
        p++
        val r = add()
        return Expr { s ->
            val a = l.eval(s)
            val b = r.eval(s)
            when (op) {
                "==" -> Values.eq(a, b)
                "!=" -> !Values.eq(a, b)
                else -> Values.compare(a, b)?.let {
                    when (op) { "<" -> it < 0; "<=" -> it <= 0; ">" -> it > 0; else -> it >= 0 }
                } ?: false
            }
        }
    }

    private fun add(): Expr {
        var l = mul()
        while (peek("+") || peek("-")) {
            val op = toks[p++].text
            val a = l
            val b = mul()
            l = Expr { s ->
                val x = a.eval(s)
                val y = b.eval(s)
                if (op == "+" && (x is String || y is String)) Values.text(x) + Values.text(y)
                else arith(op, x, y)
            }
        }
        return l
    }

    private fun mul(): Expr {
        var l = unary()
        while (peek("*") || peek("/") || peek("%")) {
            val op = toks[p++].text
            val a = l
            val b = unary()
            l = Expr { s -> arith(op, a.eval(s), b.eval(s)) }
        }
        return l
    }

    private fun arith(op: String, a: Any?, b: Any?): Any? {
        val x = Values.num(a) ?: return null
        val y = Values.num(b) ?: return null
        return when (op) {
            "+" -> x + y
            "-" -> x - y
            "*" -> x * y
            "/" -> if (y == 0.0) null else x / y
            else -> if (y == 0.0) null else x % y
        }
    }

    fun unary(): Expr {
        if (take("!")) { val e = unary(); return Expr { s -> !Values.truthy(e.eval(s)) } }
        if (take("-")) { val e = unary(); return Expr { s -> Values.num(e.eval(s))?.let { -it } } }
        return postfix()
    }

    private fun postfix(): Expr {
        var e = primary()
        while (true) {
            e = when {
                take(".") -> {
                    val t = toks[p]
                    if (t.k != K.ID && t.k != K.NUM) fail("Expected a name after '.'")
                    p++
                    val obj = e
                    val key: Any = if (t.k == K.NUM) t.num.toInt() else t.text
                    Expr { s -> Values.member(obj.eval(s), key) }
                }
                take("[") -> {
                    val idx = pipe()
                    expect("]")
                    val obj = e
                    Expr { s -> Values.member(obj.eval(s), idx.eval(s)) }
                }
                else -> return e
            }
        }
    }

    private fun primary(): Expr {
        val t = toks[p++]
        return when (t.k) {
            K.NUM -> t.num.let { n -> Expr { n } }
            K.STR -> t.text.let { str -> Expr { str } }
            K.ID -> when (t.text) {
                "true" -> Expr { true }
                "false" -> Expr { false }
                "null" -> Expr { null }
                else -> Expr { s -> s[t.text] }
            }
            K.OP -> if (t.text == "(") pipe().also { expect(")") } else fail("Unexpected '${t.text}'")
            K.END -> fail("Unexpected end")
        }
    }
}

/** `| name:arg:arg` filters. Each takes the piped value, its arguments, and the scope (for the clock setting and "now"). */
typealias FilterFn = (Any?, List<Any?>, Scope) -> Any?

object Filters {
    private fun n(v: Any?) = Values.num(v)
    private fun list(v: Any?): List<Any?> = when (v) {
        is List<*> -> v
        null -> emptyList()
        else -> listOf(v)
    }

    /** [key] in [v]: a plain key first, else a dotted path, `'value.leader'`. */
    private fun at(v: Any?, key: Any?): Any? {
        Values.member(v, key)?.let { return it }
        val parts = (key as? String)?.split('.')?.takeIf { it.size > 1 } ?: return null
        return parts.fold(v) { o, k -> Values.member(o, k) }
    }

    val all: Map<String, FilterFn> = mapOf(
        // Values
        "default" to { v, a, _ -> if (v == null || v == "" || (v is Double && v.isNaN())) a.getOrNull(0) else v },
        "round" to { v, a, _ ->
            val x = n(v)
            val places = n(a.getOrNull(0))?.toInt() ?: 0
            when {
                x == null -> null
                places <= 0 -> x.roundToLong()
                else -> BigDecimal(x).setScale(places, java.math.RoundingMode.HALF_UP).toDouble()
            }
        },
        "fixed" to { v, a, _ -> n(v)?.let { "%.${(n(a.getOrNull(0))?.toInt() ?: 2).coerceIn(0, 10)}f".format(it) } },
        "int" to { v, _, _ -> n(v)?.toLong() },
        "abs" to { v, _, _ -> n(v)?.let { abs(it) } },
        "number" to { v, a, _ ->
            n(v)?.let { NumberFormat.getNumberInstance().apply { maximumFractionDigits = n(a.getOrNull(0))?.toInt() ?: 2 }.format(it) }
        },
        "price" to { v, _, _ ->
            n(v)?.let { p ->
                when {
                    abs(p) >= 10_000 -> "%,.0f".format(p)
                    abs(p) >= 1 -> "%,.2f".format(p)
                    else -> "%.4f".format(p)
                }
            }
        },
        "signed" to { v, _, _ -> n(v)?.let { (if (it > 0) "+" else "") + (if (v is String) v.trim() else Values.text(it)) } },
        // Text
        "upper" to { v, _, _ -> Values.text(v).uppercase() },
        "lower" to { v, _, _ -> Values.text(v).lowercase() },
        "capitalize" to { v, _, _ -> Values.text(v).replaceFirstChar { it.titlecase() } },
        "trim" to { v, _, _ -> Values.text(v).trim() },
        "truncate" to { v, a, _ ->
            val s = Values.text(v)
            val max = n(a.getOrNull(0))?.toInt() ?: 40
            if (s.length <= max) s else s.take((max - 1).coerceAtLeast(0)).trimEnd() + "…"
        },
        // Part of a text, for feeds that put several things in one field: after:'x' is what follows
        // the first x (null when there's no x), before:'x' what comes ahead of it (all of it when there's none).
        "after" to { v, a, _ ->
            val s = v?.let(Values::text)
            val x = a.getOrNull(0)?.let(Values::text).orEmpty()
            if (s == null || x.isEmpty()) s else s.indexOf(x).takeIf { it >= 0 }?.let { s.substring(it + x.length) }
        },
        "before" to { v, a, _ ->
            val s = v?.let(Values::text)
            val x = a.getOrNull(0)?.let(Values::text).orEmpty()
            if (s == null || x.isEmpty()) s else s.substringBefore(x)
        },
        "url" to { v, _, _ -> URLEncoder.encode(Values.text(v), "UTF-8") },
        // Lists
        "count" to { v, _, _ ->
            when (v) {
                is Collection<*> -> v.size
                is Map<*, *> -> v.size
                is String -> v.length
                null -> 0
                else -> 1
            }
        },
        "first" to { v, _, _ -> list(v).firstOrNull() },
        "last" to { v, _, _ -> list(v).lastOrNull() },
        "take" to { v, a, _ -> list(v).take(n(a.getOrNull(0))?.toInt()?.coerceAtLeast(0) ?: 1) },
        "skip" to { v, a, _ -> list(v).drop(n(a.getOrNull(0))?.toInt()?.coerceAtLeast(0) ?: 1) },
        "reverse" to { v, _, _ -> list(v).reversed() },
        "join" to { v, a, _ -> list(v).joinToString(a.getOrNull(0)?.let(Values::text) ?: ", ") { Values.text(it) } },
        "map" to { v, a, _ -> list(v).map { at(it, a.getOrNull(0)) } },
        // where:'key' keeps items whose key is true; where:'key':x those whose key is x.
        "where" to { v, a, _ ->
            if (a.size < 2) list(v).filter { Values.truthy(at(it, a.getOrNull(0))) }
            else list(v).filter { Values.eq(at(it, a[0]), a[1]) }
        },
        // An object as a list of {key, value}, by key: for data keyed by name, like {"GA": {...}}.
        "entries" to { v, _, _ ->
            when (v) {
                is Map<*, *> -> v.entries.sortedBy { Values.text(it.key) }.map { mapOf("key" to it.key, "value" to it.value) }
                else -> list(v).mapIndexed { i, x -> mapOf("key" to i, "value" to x) }
            }
        },
        "sum" to { v, _, _ -> list(v).mapNotNull(::n).sum() },
        "min" to { v, _, _ -> list(v).mapNotNull(::n).minOrNull() },
        "max" to { v, _, _ -> list(v).mapNotNull(::n).maxOrNull() },
        // Dates and times: ISO-8601 text, RSS dates, or epoch seconds/milliseconds
        "time" to { v, a, s ->
            Dates.read(v, s)?.takeIf { !it.second }?.first?.let { t ->
                val h24 = a.getOrNull(0)?.let(Values::truthy) ?: Values.truthy(Values.member(s["hud"], "clock24h"))
                t.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a"))
            }
        },
        "date" to { v, a, s ->
            Dates.read(v, s)?.first?.let { t ->
                runCatching { t.format(DateTimeFormatter.ofPattern(Values.text(a.getOrNull(0) ?: "EEE, MMM d"))) }.getOrNull()
            }
        },
        "day" to { v, _, s ->
            Dates.read(v, s)?.first?.toLocalDate()?.let { d ->
                val today = s.now.toLocalDate()
                when (d) {
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    today.minusDays(1) -> "Yesterday"
                    else -> d.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                }
            }
        },
        "weekday" to { v, _, s ->
            Dates.read(v, s)?.first?.toLocalDate()?.let { d ->
                if (d == s.now.toLocalDate()) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        },
        "daysUntil" to { v, _, s ->
            Dates.read(v, s)?.first?.toLocalDate()?.let { java.time.temporal.ChronoUnit.DAYS.between(s.now.toLocalDate(), it) }
        },
        "ago" to { v, _, s ->
            Dates.read(v, s)?.first?.let { t ->
                val mins = java.time.Duration.between(t, s.now.toLocalDateTime()).toMinutes()
                when {
                    mins < 1 -> "just now"
                    mins < 60 -> "$mins min ago"
                    mins < 48 * 60 -> "${mins / 60} h ago"
                    else -> "${mins / 1440} days ago"
                }
            }
        },
        // WMO weather codes, as Open-Meteo returns them
        "wmoIcon" to { v, _, _ -> n(v)?.let { Wmo.describe(it.toInt()).second } },
        "wmoText" to { v, _, _ -> n(v)?.let { Wmo.describe(it.toInt()).first } },
    )
}

internal object Dates {
    /** The value as a local date-time, and whether it was only a date. */
    fun read(v: Any?, s: Scope): Pair<LocalDateTime, Boolean>? {
        val zone = s.now.zone
        return when (v) {
            is Number -> {
                val x = v.toDouble()
                val instant = if (abs(x) > 1e11) Instant.ofEpochMilli(x.toLong()) else Instant.ofEpochSecond(x.toLong())
                instant.atZone(zone).toLocalDateTime() to false
            }
            is String -> {
                val t = v.trim()
                runCatching { LocalDateTime.parse(t) to false }.getOrNull()
                    ?: runCatching { OffsetDateTime.parse(t).atZoneSameInstant(zone).toLocalDateTime() to false }.getOrNull()
                    ?: runCatching { ZonedDateTime.parse(t).withZoneSameInstant(zone).toLocalDateTime() to false }.getOrNull()
                    ?: runCatching { Instant.parse(t).atZone(zone).toLocalDateTime() to false }.getOrNull()
                    ?: runCatching { LocalDate.parse(t).atStartOfDay() to true }.getOrNull()
                    ?: runCatching {
                        ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).withZoneSameInstant(zone).toLocalDateTime() to false
                    }.getOrNull()
            }
            else -> null
        }
    }
}
