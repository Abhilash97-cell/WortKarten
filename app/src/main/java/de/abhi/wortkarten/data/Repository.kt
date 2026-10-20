package de.abhi.wortkarten.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * All data lives in one local JSON file (app-private storage). Saved after every change.
 * Word lists in assets/words/ are merged in on every start: new words are added,
 * changed words are refreshed, and your progress is always kept.
 */
class Repository(private val context: Context) {
    private val file = File(context.filesDir, FILE)

    private val _words = MutableStateFlow<List<Word>>(emptyList())
    val words: StateFlow<List<Word>> = _words
    private val _stats = MutableStateFlow<Map<String, DayStat>>(emptyMap())
    val stats: StateFlow<Map<String, DayStat>> = _stats
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    /** Words you deleted from the built-in lists, so updates don't bring them back. */
    private var deletedKeys: Set<String> = emptySet()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (file.exists()) runCatching { fromJson(JSONObject(file.readText())) }
        mergeAssets()
        applyNewCategories()
        saveNow()
    }

    private fun readAssets(): List<Csv.Row> {
        val names = context.assets.list("words")?.filter { it.endsWith(".csv") }?.sorted() ?: emptyList()
        return names.flatMap { n ->
            context.assets.open("words/$n").bufferedReader(Charsets.UTF_8).use { Csv.parse(it.readText()) }
        }
    }

    private fun mergeAssets() {
        val rows = runCatching { readAssets() }.getOrDefault(emptyList())
        val map = LinkedHashMap<String, Word>()
        _words.value.forEach { map[it.key] = it }
        var id = nextId(_words.value)
        for (r in rows) {
            val k = wordKey(r.category, r.german)
            if (k in deletedKeys) continue
            val old = map[k]
            map[k] = when {
                old == null -> r.toWord(id++, k, custom = false)
                old.custom -> old
                else -> old.withContentOf(r)
            }
        }
        _words.value = map.values.toList()
    }

    /** New categories are switched on, except "Technik…" categories (off by default). */
    private fun applyNewCategories() {
        val s = _settings.value
        val cats = _words.value.map { it.category }.toSet()
        val fresh = cats - s.seenCategories
        if (fresh.isEmpty()) return
        _settings.value = s.copy(
            seenCategories = s.seenCategories + fresh,
            disabledCategories = s.disabledCategories + fresh.filter { it.startsWith("Technik") }
        )
    }

    // ---------------- changes ----------------
    suspend fun update(w: Word) = updateAll(listOf(w))

    suspend fun updateAll(changed: List<Word>) {
        if (changed.isEmpty()) return
        val m = changed.associateBy { it.id }
        mutate { list -> list.map { m[it.id] ?: it } }
    }

    suspend fun delete(id: Long) {
        val w = _words.value.firstOrNull { it.id == id } ?: return
        deletedKeys = deletedKeys + w.key
        mutate { list -> list.filterNot { it.id == id } }
    }

    /** Returns false if the word already exists in that category. */
    suspend fun add(w: Word): Boolean {
        val k = wordKey(w.category, w.german)
        if (_words.value.any { it.key == k }) return false
        mutate { list -> list + w.copy(id = nextId(list), key = k, custom = true) }
        return true
    }

    /** Returns the number of words added (duplicates are skipped). */
    suspend fun importRows(rows: List<Csv.Row>): Int {
        var added = 0
        mutate { list ->
            val existing = list.map { it.key }.toHashSet()
            var id = nextId(list)
            val new = rows.mapNotNull { r ->
                val k = wordKey(r.category, r.german)
                if (existing.add(k)) r.toWord(id++, k, custom = true) else null
            }
            added = new.size
            list + new
        }
        return added
    }

    suspend fun resetProgress() {
        _stats.value = emptyMap()
        mutate { list -> list.map { it.copy(rec = Card(), prod = Card(), reps = 0, lapses = 0, known = false) } }
    }

    suspend fun recordReview(wasNew: Boolean) {
        val key = LocalDate.now().toString()
        val d = _stats.value[key] ?: DayStat()
        _stats.value = _stats.value + (key to d.copy(
            reviewed = d.reviewed + 1,
            learnedNew = d.learnedNew + if (wasNew) 1 else 0
        ))
        persist()
    }

    suspend fun setSettings(s: Settings) { _settings.value = s; persist() }

    fun backupJson(): String = toJson().toString()

    suspend fun restoreBackup(text: String): Boolean = runCatching {
        fromJson(JSONObject(text))
        mergeAssets()
        applyNewCategories()
        persist()
    }.isSuccess

    private suspend fun mutate(block: (List<Word>) -> List<Word>) {
        _words.value = block(_words.value)
        applyNewCategories()
        persist()
    }

    private fun nextId(list: List<Word>) = (list.maxOfOrNull { it.id } ?: 0L) + 1
    private suspend fun persist() = withContext(Dispatchers.IO) { saveNow() }

    @Synchronized
    private fun saveNow() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson().toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    // ---------------- JSON ----------------
    private fun toJson() = JSONObject().apply {
        put("version", 2)
        put("words", JSONArray().apply {
            _words.value.forEach { w ->
                put(
                    JSONObject().put("id", w.id).put("y", w.key).put("de", w.german).put("pl", w.plural)
                        .put("gr", w.grammar).put("tr", w.translation).put("c", w.category).put("s", w.subtopic)
                        .put("lv", w.level).put("rg", w.register).put("ex", w.example).put("exe", w.exampleEn)
                        .put("n", w.note).put("rb", w.rec.box).put("rd", w.rec.due).put("pb", w.prod.box)
                        .put("pd", w.prod.due).put("r", w.reps).put("l", w.lapses).put("k", w.known)
                        .put("cu", w.custom)
                )
            }
        })
        put("stats", JSONObject().apply {
            _stats.value.forEach { (k, v) -> put(k, JSONObject().put("r", v.reviewed).put("n", v.learnedNew)) }
        })
        val s = _settings.value
        put(
            "settings", JSONObject().put("goal", s.dailyGoal).put("basic", s.basicShare)
                .put("off", JSONArray(s.disabledCategories.toList())).put("seen", JSONArray(s.seenCategories.toList()))
                .put("both", s.bothDirections).put("rem", s.reminderOn).put("rh", s.reminderHour)
                .put("rm", s.reminderMinute)
        )
        put("deleted", JSONArray(deletedKeys.toList()))
    }

    private fun fromJson(o: JSONObject) {
        val arr = o.getJSONArray("words")
        _words.value = List(arr.length()) { i ->
            val j = arr.getJSONObject(i)
            val de = j.getString("de")
            val cat = j.optString("c", j.optString("t", "Eigene Wörter"))
            Word(
                id = j.getLong("id"), key = j.optString("y").ifEmpty { wordKey(cat, de) }, german = de,
                plural = j.optString("pl"), grammar = j.optString("gr"), translation = j.getString("tr"),
                category = cat, subtopic = j.optString("s"), level = j.optString("lv", "B1"),
                register = j.optString("rg"), example = j.optString("ex"), exampleEn = j.optString("exe"),
                note = j.optString("n"), rec = Card(j.optInt("rb"), j.optLong("rd")),
                prod = Card(j.optInt("pb"), j.optLong("pd")), reps = j.optInt("r"), lapses = j.optInt("l"),
                known = j.optBoolean("k"), custom = j.optBoolean("cu")
            )
        }
        val st = o.optJSONObject("stats") ?: JSONObject()
        _stats.value = st.keys().asSequence().associateWith { k ->
            st.getJSONObject(k).let { DayStat(it.optInt("r"), it.optInt("n")) }
        }
        o.optJSONObject("settings")?.let { s ->
            _settings.value = Settings(
                dailyGoal = s.optInt("goal", 15), basicShare = s.optInt("basic", 20),
                disabledCategories = s.optJSONArray("off").strings(), seenCategories = s.optJSONArray("seen").strings(),
                bothDirections = s.optBoolean("both", true), reminderOn = s.optBoolean("rem", false),
                reminderHour = s.optInt("rh", 19), reminderMinute = s.optInt("rm", 0)
            )
        }
        deletedKeys = o.optJSONArray("deleted").strings()
    }

    companion object {
        const val FILE = "wortkarten.json"

        /** Read-only count of due reviews, used by the daily reminder. */
        fun countDue(context: Context): Int = runCatching {
            val f = File(context.filesDir, FILE)
            if (!f.exists()) return 0
            val o = JSONObject(f.readText())
            val s = o.optJSONObject("settings")
            val both = s?.optBoolean("both", true) ?: true
            val off = s?.optJSONArray("off").strings()
            val arr = o.getJSONArray("words")
            val now = System.currentTimeMillis()
            var n = 0
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                if (j.optInt("r") == 0 || j.optBoolean("k") || j.optString("c") in off) continue
                if (j.optLong("rd") <= now) n++
                if (both && j.optLong("pd") <= now) n++
            }
            n
        }.getOrDefault(0)
    }
}

private fun JSONArray?.strings(): Set<String> =
    if (this == null) emptySet() else (0 until length()).map { getString(it) }.toSet()
