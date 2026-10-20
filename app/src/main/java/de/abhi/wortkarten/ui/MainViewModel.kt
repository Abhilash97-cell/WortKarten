package de.abhi.wortkarten.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.abhi.wortkarten.data.*
import de.abhi.wortkarten.reminder.Reminder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One card in a session: a word, a direction, and whether it's the first look at a new word. */
data class Item(val id: Long, val dir: Dir, val intro: Boolean = false)

enum class Mode { ALL, PRACTICE, NEW }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
    val words = repo.words
    val stats = repo.stats
    val settings = repo.settings
    val ready = MutableStateFlow(false)

    /** Text coming from "Teilen → WortKarten". */
    val sharedText = MutableStateFlow<String?>(null)
    /** Short messages shown as a toast. */
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { repo.load(); ready.value = true }
    }

    private fun enabled(w: Word) = w.category !in settings.value.disabledCategories

    fun counts(): Map<Status, Int> = words.value.filter(::enabled).groupingBy { it.status }.eachCount()
    fun today(): DayStat = stats.value[LocalDate.now().toString()] ?: DayStat()

    // ======================= session =======================
    private val _queue = MutableStateFlow<List<Item>>(emptyList())
    private val spareBasic = ArrayDeque<Long>()
    private val spareAdv = ArrayDeque<Long>()
    val sessionDone = MutableStateFlow(0)

    val current: StateFlow<Pair<Word, Item>?> = combine(_queue, words) { q, ws ->
        q.firstOrNull()?.let { item -> ws.firstOrNull { it.id == item.id }?.let { w -> w to item } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val remaining: StateFlow<Int> = _queue.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** onlyDue = normal reviews; otherwise all "Üben" cards (for the "Nur Üben" button). */
    private fun reviewItems(onlyDue: Boolean): List<Item> {
        val now = System.currentTimeMillis()
        val both = settings.value.bothDirections
        val rec = mutableListOf<Pair<Long, Item>>()
        val prod = mutableListOf<Pair<Long, Item>>()
        words.value.filter { enabled(it) && it.reps > 0 && !it.known }.forEach { w ->
            if (onlyDue) {
                if (w.rec.due <= now) rec += w.rec.due to Item(w.id, Dir.REC)
                if (both && w.prod.due <= now) prod += w.prod.due to Item(w.id, Dir.PROD)
            } else if (w.status == Status.PRACTICE) {
                if (w.rec.box < Srs.LEARNED_BOX) rec += w.rec.due to Item(w.id, Dir.REC)
                if (both && w.prod.box < Srs.LEARNED_BOX) prod += w.prod.due to Item(w.id, Dir.PROD)
            }
        }
        // Recognition first, then production, so both directions of one word aren't back to back.
        return rec.sortedBy { it.first }.map { it.second } + prod.sortedBy { it.first }.map { it.second }
    }

    fun dueCount() = reviewItems(true).size

    /** Spreads words of different categories evenly: A, B, C, A, B, C … */
    private fun roundRobin(ws: List<Word>): List<Word> {
        val groups = ws.sortedBy { it.id }.groupBy { it.category }.values.map { ArrayDeque(it) }
        val out = mutableListOf<Word>()
        while (groups.any { it.isNotEmpty() }) groups.forEach { g -> g.removeFirstOrNull()?.let { out += it } }
        return out
    }

    /**
     * Picks new words: B1 → B2 → C1 first, plus a share of A1–A2 (setting "basicShare", default 20 %).
     * If one group runs out, the other fills up.
     */
    private fun pickNew(count: Int, levels: Set<String>): List<Long> {
        val pool = words.value.filter { enabled(it) && it.status == Status.NEW && it.level in levels }
        fun ordered(ws: List<Word>) = LEVELS.flatMap { lv -> roundRobin(ws.filter { it.level == lv }) }.map { it.id }
        val basic = ordered(pool.filter { it.isBasic })
        val adv = ordered(pool.filterNot { it.isBasic })
        var nb = Math.round(count * settings.value.basicShare / 100f).coerceAtMost(basic.size)
        val na = (count - nb).coerceAtMost(adv.size)
        nb = (count - na).coerceAtMost(basic.size)
        spareBasic.clear(); spareBasic.addAll(basic.drop(nb))
        spareAdv.clear(); spareAdv.addAll(adv.drop(na))
        return interleave(adv.take(na), basic.take(nb))
    }

    fun newAvailable(levels: Set<String> = LEVELS.toSet()) =
        words.value.count { enabled(it) && it.status == Status.NEW && it.level in levels }

    fun startSession(mode: Mode, levels: Set<String> = LEVELS.toSet()) {
        sessionDone.value = 0
        spareBasic.clear(); spareAdv.clear()
        _queue.value = when (mode) {
            Mode.ALL -> {
                val left = (settings.value.dailyGoal - today().learnedNew).coerceAtLeast(0)
                interleave(reviewItems(true), pickNew(left, LEVELS.toSet()).map { Item(it, Dir.REC, intro = true) })
            }
            Mode.PRACTICE -> reviewItems(false).take(60)
            Mode.NEW -> pickNew(settings.value.dailyGoal, levels).map { Item(it, Dir.REC, intro = true) }
        }
    }

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        val out = ArrayList<T>(a.size + b.size)
        val step = maxOf(1, a.size / (b.size + 1))
        var ai = 0
        for (x in b) {
            repeat(step) { if (ai < a.size) out += a[ai++] }
            out += x
        }
        while (ai < a.size) out += a[ai++]
        return out
    }

    private fun pop() { _queue.value = _queue.value.drop(1) }
    private fun insertAt(gap: Int, item: Item) {
        val q = _queue.value.toMutableList()
        q.add(minOf(gap, q.size), item)
        _queue.value = q
    }

    /** New card → "Kenne ich schon": never asked again; another new word takes its place. */
    fun markKnown() = viewModelScope.launch {
        val (w, _) = current.value ?: return@launch
        pop()
        repo.update(w.copy(known = true))
        val next = (if (w.isBasic) spareBasic.removeFirstOrNull() else spareAdv.removeFirstOrNull())
            ?: spareAdv.removeFirstOrNull() ?: spareBasic.removeFirstOrNull()
        next?.let { _queue.value = _queue.value + Item(it, Dir.REC, intro = true) }
    }

    /** New card → "Lernen": becomes 🔁 Üben and is tested again a few cards later (both directions). */
    fun startLearning() = viewModelScope.launch {
        val (w, _) = current.value ?: return@launch
        pop()
        val now = System.currentTimeMillis()
        repo.update(w.copy(reps = 1, rec = Srs.fresh(now), prod = Srs.fresh(now)))
        repo.recordReview(wasNew = true)
        insertAt(3, Item(w.id, Dir.REC))
        if (settings.value.bothDirections) insertAt(7, Item(w.id, Dir.PROD))
    }

    fun answer(correct: Boolean) = viewModelScope.launch {
        val (w, item) = current.value ?: return@launch
        pop()
        val c = if (correct) Srs.correct(w.card(item.dir)) else Srs.wrong()
        var nw = w.withCard(item.dir, c).copy(reps = w.reps + 1, lapses = w.lapses + if (correct) 0 else 1)
        if (!settings.value.bothDirections) nw = nw.copy(prod = c) // one direction only → mirror progress
        repo.update(nw)
        repo.recordReview(wasNew = false)
        sessionDone.value++
        if (!correct) insertAt(4, item)
    }

    fun endSession() { _queue.value = emptyList() }

    // ======================= article quiz =======================
    val quizWord = MutableStateFlow<Word?>(null)
    val quizRound = MutableStateFlow(0)
    val quizScore = MutableStateFlow(0 to 0)

    fun nextQuiz() {
        val nouns = words.value.filter { enabled(it) && it.article != null }
        val seen = nouns.filter { it.status != Status.NEW }
        val pool = if (seen.size >= 15) seen else nouns
        quizWord.value = pool.filter { it.id != quizWord.value?.id }.randomOrNull() ?: pool.randomOrNull()
        quizRound.value++
    }

    fun answerQuiz(correct: Boolean) {
        val (r, t) = quizScore.value
        quizScore.value = (r + if (correct) 1 else 0) to (t + 1)
    }

    // ======================= editing =======================
    fun addWord(w: Word) = viewModelScope.launch {
        message.value = if (repo.add(w)) "„${w.german}“ hinzugefügt" else "„${w.german}“ gibt es schon in ${w.category}"
    }
    fun editWord(w: Word) = viewModelScope.launch { repo.update(w.copy(custom = true)) }
    fun deleteWord(id: Long) = viewModelScope.launch { repo.delete(id) }

    /** Manually move a word to another status. */
    fun setStatus(w: Word, s: Status) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        repo.update(
            when (s) {
                Status.NEW -> w.copy(known = false, reps = 0, lapses = 0, rec = Card(), prod = Card())
                Status.KNOWN -> w.copy(known = true)
                Status.PRACTICE -> w.copy(known = false, reps = maxOf(1, w.reps), rec = Card(1, now), prod = Card(1, now))
                Status.LEARNED -> w.copy(known = false, reps = maxOf(1, w.reps), rec = Srs.learned(now), prod = Srs.learned(now))
            }
        )
    }

    /** Topics screen → "A1–A2 als bekannt markieren". */
    fun markBasicKnown(category: String) = viewModelScope.launch {
        val list = words.value.filter { it.category == category && it.isBasic && it.status == Status.NEW }
        repo.updateAll(list.map { it.copy(known = true) })
        message.value = "${list.size} A1–A2-Wörter als bekannt markiert"
    }

    fun toggleCategory(c: String) = viewModelScope.launch {
        val s = settings.value
        val off = if (c in s.disabledCategories) s.disabledCategories - c else s.disabledCategories + c
        repo.setSettings(s.copy(disabledCategories = off))
    }

    fun setGoal(g: Int) = viewModelScope.launch { repo.setSettings(settings.value.copy(dailyGoal = g)) }
    fun setBasicShare(p: Int) = viewModelScope.launch { repo.setSettings(settings.value.copy(basicShare = p)) }
    fun setBothDirections(b: Boolean) = viewModelScope.launch { repo.setSettings(settings.value.copy(bothDirections = b)) }

    fun setReminder(on: Boolean) = viewModelScope.launch {
        val s = settings.value.copy(reminderOn = on)
        repo.setSettings(s)
        if (on) Reminder.schedule(getApplication<Application>(), s.reminderHour, s.reminderMinute) else Reminder.cancel(getApplication<Application>())
    }

    fun setReminderTime(h: Int, m: Int) = viewModelScope.launch {
        val s = settings.value.copy(reminderHour = h, reminderMinute = m)
        repo.setSettings(s)
        if (s.reminderOn) Reminder.schedule(getApplication<Application>(), h, m)
    }

    fun resetAll() = viewModelScope.launch { repo.resetProgress() }
    suspend fun importCsv(text: String): Int = repo.importRows(Csv.parse(text))
    fun exportCsv(): String = Csv.export(words.value)
    fun backupJson(): String = repo.backupJson()
    suspend fun restoreBackup(text: String) = repo.restoreBackup(text)

    fun streak(): Int {
        var d = LocalDate.now()
        if ((stats.value[d.toString()]?.reviewed ?: 0) == 0) d = d.minusDays(1) // today not done yet is fine
        var n = 0
        while ((stats.value[d.toString()]?.reviewed ?: 0) > 0) { n++; d = d.minusDays(1) }
        return n
    }
}
