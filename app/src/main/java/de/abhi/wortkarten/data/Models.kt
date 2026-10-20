package de.abhi.wortkarten.data

/** The four card statuses you see in the app. */
enum class Status(val label: String, val emoji: String) {
    NEW("Neu", "🆕"),
    KNOWN("Kenne ich schon", "✅"),
    PRACTICE("Üben", "🔁"),
    LEARNED("Gelernt", "🟢")
}

/** REC = Deutsch → Übersetzung (verstehen). PROD = Übersetzung → Deutsch (selbst sagen). */
enum class Dir { REC, PROD }

val LEVELS = listOf("A1", "A2", "B1", "B2", "C1")

/** Spaced-repetition state of one direction. */
data class Card(val box: Int = 0, val due: Long = 0L)

data class Word(
    val id: Long,
    val key: String,
    val german: String,
    val plural: String = "",
    val grammar: String = "",          // verb forms, case after preposition …
    val translation: String,
    val category: String,
    val subtopic: String = "",
    val level: String = "B1",
    val register: String = "",         // "" = neutral, "umg", "formell"
    val example: String = "",
    val exampleEn: String = "",
    val note: String = "",
    val rec: Card = Card(),
    val prod: Card = Card(),
    val reps: Int = 0,
    val lapses: Int = 0,
    val known: Boolean = false,
    val custom: Boolean = false        // added/edited by you → never overwritten by word-list updates
) {
    val status: Status
        get() = when {
            known -> Status.KNOWN
            reps == 0 -> Status.NEW
            rec.box >= Srs.LEARNED_BOX && prod.box >= Srs.LEARNED_BOX -> Status.LEARNED
            else -> Status.PRACTICE
        }

    val isBasic get() = level == "A1" || level == "A2"

    fun card(d: Dir) = if (d == Dir.REC) rec else prod
    fun withCard(d: Dir, c: Card) = if (d == Dir.REC) copy(rec = c) else copy(prod = c)

    /** "der", "die" or "das" for simple nouns like "der Termin", otherwise null. */
    val article: String?
        get() {
            val parts = german.trim().split(" ")
            if (parts.size != 2 || parts[1].isEmpty() || !parts[1][0].isUpperCase()) return null
            return parts[0].lowercase().takeIf { it == "der" || it == "die" || it == "das" }
        }
    val noun: String get() = german.trim().substringAfter(' ')

    /** Text for speech output: removes hints like "(Partikel)" and "…". */
    val speakText: String
        get() = german.replace(Regex("\\s*\\([^)]*\\)"), "").replace("…", "").trim()

    val registerLabel: String?
        get() = when {
            register.startsWith("umg", ignoreCase = true) -> "umgangssprachlich"
            register.startsWith("form", ignoreCase = true) -> "formell"
            else -> null
        }
}

fun wordKey(category: String, german: String) = (category.trim() + "|" + german.trim()).lowercase()
fun levelIndex(l: String) = LEVELS.indexOf(l).let { if (it < 0) 2 else it }

data class DayStat(val reviewed: Int = 0, val learnedNew: Int = 0)

data class Settings(
    val dailyGoal: Int = 15,
    val basicShare: Int = 20,                 // % of new words from A1–A2
    val disabledCategories: Set<String> = emptySet(),
    val seenCategories: Set<String> = emptySet(),
    val bothDirections: Boolean = true,
    val reminderOn: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0
)
