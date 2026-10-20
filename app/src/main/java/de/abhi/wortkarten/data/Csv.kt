package de.abhi.wortkarten.data

/**
 * Word list format (UTF-8, semicolon-separated, header line recommended):
 * german;plural;grammar;translation;category;subtopic;level;register;example;example_en;note
 *
 * Only german + translation are required. With a header, columns may be in any order.
 * The simple 4-column format (german;translation;topic;example) is also accepted.
 */
object Csv {
    val COLUMNS = listOf(
        "german", "plural", "grammar", "translation", "category", "subtopic",
        "level", "register", "example", "example_en", "note"
    )
    private val LEGACY = listOf("german", "translation", "category", "example")

    data class Row(
        val german: String, val plural: String, val grammar: String, val translation: String,
        val category: String, val subtopic: String, val level: String, val register: String,
        val example: String, val exampleEn: String, val note: String
    )

    fun parse(text: String, defaultCategory: String = "Eigene Wörter"): List<Row> {
        val lines = text.removePrefix("\uFEFF").lines().map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) return emptyList()
        val first = lines.first()
        val sep = if (first.count { it == ';' } >= first.count { it == ',' }) ';' else ','
        val firstFields = split(first, sep).map { it.trim().lowercase() }
        val hasHeader = firstFields.firstOrNull() in setOf("german", "deutsch")
        val header = if (hasHeader) firstFields.map { if (it == "topic") "category" else it } else null

        return lines.drop(if (hasHeader) 1 else 0).mapNotNull { line ->
            val f = split(line, sep).map { it.trim() }
            val cols = header ?: if (f.size <= 4) LEGACY else COLUMNS
            fun g(name: String): String {
                val i = cols.indexOf(name)
                return if (i >= 0) f.getOrNull(i).orEmpty() else ""
            }
            val de = g("german")
            val tr = g("translation")
            if (de.isEmpty() || tr.isEmpty()) return@mapNotNull null
            Row(
                de, g("plural"), g("grammar"), tr, g("category").ifEmpty { defaultCategory },
                g("subtopic"), normLevel(g("level")), g("register"), g("example"), g("example_en"), g("note")
            )
        }
    }

    private fun normLevel(s: String) = s.trim().uppercase().takeIf { it in LEVELS } ?: "B1"

    private fun split(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == sep && !q -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    fun export(words: List<Word>): String = buildString {
        append(COLUMNS.joinToString(";")).append('\n')
        words.forEach { w ->
            append(
                listOf(
                    w.german, w.plural, w.grammar, w.translation, w.category, w.subtopic,
                    w.level, w.register, w.example, w.exampleEn, w.note
                ).joinToString(";") { esc(it) }
            ).append('\n')
        }
    }

    private fun esc(s: String) =
        if (s.contains(';') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

fun Csv.Row.toWord(id: Long, key: String, custom: Boolean) = Word(
    id = id, key = key, german = german, plural = plural, grammar = grammar, translation = translation,
    category = category, subtopic = subtopic, level = level, register = register,
    example = example, exampleEn = exampleEn, note = note, custom = custom
)

/** Refresh a word's content from an updated word list while keeping its progress. */
fun Word.withContentOf(r: Csv.Row) = copy(
    german = r.german, plural = r.plural, grammar = r.grammar, translation = r.translation,
    category = r.category, subtopic = r.subtopic, level = r.level, register = r.register,
    example = r.example, exampleEn = r.exampleEn, note = r.note
)
