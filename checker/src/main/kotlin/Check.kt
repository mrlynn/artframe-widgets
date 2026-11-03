import com.mrlynn.artframe.widget.Json
import com.mrlynn.artframe.widget.TextLint
import com.mrlynn.artframe.widget.WidgetSpec
import com.mrlynn.artframe.widget.WidgetSpecException
import java.io.File

/**
 * Parses each widget file with the TV's parser and prints one JSON report:
 * `[{ "file", "ok", "problems", "warnings", "id", "hosts", "chosenSite" }]`. tools/build_index.py reads it.
 * Warnings (phone numbers, web addresses, "scan" or "call" wording in a widget's own text) are for
 * a reviewer to read; they never make a widget fail.
 * Exits 0 either way; the report says what failed.
 */
fun main(args: Array<String>) {
    val reports = args.map { path ->
        val f = File(path)
        val warnings = try { TextLint.warnings(Json.parse(f.readText()) as Map<*, *>) } catch (e: Exception) { emptyList() }
        try {
            val spec = WidgetSpec.parse(f.readText())
            val http = spec.data.values.filter { it.kind == "http" }
            mapOf(
                "file" to path,
                "ok" to true,
                "problems" to emptyList<String>(),
                "warnings" to warnings,
                "id" to spec.id,
                "hosts" to http.mapNotNull { it.host }.distinct().sorted(),
                "chosenSite" to http.any { it.host == null },
            )
        } catch (e: WidgetSpecException) {
            mapOf("file" to path, "ok" to false, "problems" to e.problems, "warnings" to warnings)
        } catch (e: Exception) {
            mapOf("file" to path, "ok" to false, "problems" to listOf("${e.javaClass.simpleName}: ${e.message}"), "warnings" to warnings)
        }
    }
    println(json(reports))
}

private fun json(v: Any?): String = when (v) {
    null -> "null"
    is Boolean, is Number -> v.toString()
    is String -> buildString {
        append('"')
        for (c in v) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }
    is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> json(k.toString()) + ":" + json(x) }
    is Iterable<*> -> v.joinToString(",", "[", "]") { json(it) }
    else -> json(v.toString())
}
