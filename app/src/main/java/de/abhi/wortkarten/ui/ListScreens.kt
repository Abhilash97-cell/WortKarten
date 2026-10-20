@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.abhi.wortkarten.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.abhi.wortkarten.data.LEVELS
import de.abhi.wortkarten.data.Status
import de.abhi.wortkarten.data.Word

@Composable
fun TopicsScreen(vm: MainViewModel, go: (Screen) -> Unit, back: () -> Unit) {
    val words by vm.words.collectAsState()
    val settings by vm.settings.collectAsState()
    val cats = remember(words) { words.groupBy { it.category }.toSortedMap() }
    var confirmBasic by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Themen") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } })
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Text(
                    "Haken = Thema wird beim Lernen verwendet. Tippen = Wörter ansehen.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall
                )
            }
            items(cats.entries.toList(), key = { it.key }) { (cat, list) ->
                val c = list.groupingBy { it.status }.eachCount()
                val touched = list.count { it.status != Status.NEW }
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(cat) },
                    supportingContent = {
                        Column {
                            Text(Status.entries.joinToString("  ") { "${it.emoji} ${c[it] ?: 0}" } + "   / ${list.size}")
                            LinearProgressIndicator(
                                progress = { touched.toFloat() / list.size },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Row {
                            Checkbox(cat !in settings.disabledCategories, { vm.toggleCategory(cat) })
                            Box {
                                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mehr") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Wörter ansehen") },
                                        onClick = { menu = false; go(Screen.WordList(category = cat)) })
                                    DropdownMenuItem(text = { Text("A1–A2 als bekannt markieren") },
                                        onClick = { menu = false; confirmBasic = cat })
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { go(Screen.WordList(category = cat)) }
                )
                HorizontalDivider()
            }
        }
    }
    confirmBasic?.let { cat ->
        AlertDialog(
            onDismissRequest = { confirmBasic = null },
            title = { Text("A1–A2 als bekannt markieren?") },
            text = { Text("Alle neuen A1- und A2-Wörter in „$cat“ werden auf ✅ Kenne ich schon gesetzt. Rückgängig: in der Wortliste den Status ändern.") },
            confirmButton = { TextButton({ vm.markBasicKnown(cat); confirmBasic = null }) { Text("Markieren") } },
            dismissButton = { TextButton({ confirmBasic = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
fun WordListScreen(vm: MainViewModel, category: String?, initialStatus: Status?, speak: (String) -> Unit, back: () -> Unit) {
    val words by vm.words.collectAsState()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(initialStatus) }
    var levels by remember { mutableStateOf(emptySet<String>()) }
    var sub by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Word?>(null) }
    var adding by remember { mutableStateOf(false) }

    val inCat = remember(words, category) { words.filter { category == null || it.category == category } }
    val subtopics = remember(inCat) { inCat.map { it.subtopic }.filter { it.isNotBlank() }.distinct() }
    val shown = remember(inCat, query, status, levels, sub) {
        inCat.filter {
            (status == null || it.status == status) &&
                (levels.isEmpty() || it.level in levels) &&
                (sub == null || it.subtopic == sub) &&
                (query.isBlank() || it.german.contains(query, true) || it.translation.contains(query, true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(category ?: "Alle Wörter") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } })
        },
        floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, "Hinzufügen") } }
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth(),
                        placeholder = { Text("Suchen (Deutsch oder Übersetzung)…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(status == null, { status = null }, label = { Text("Alle") })
                        Status.entries.forEach { s ->
                            FilterChip(status == s, { status = if (status == s) null else s },
                                label = { Text(s.emoji + " " + s.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LEVELS.forEach { lv ->
                            FilterChip(lv in levels, { levels = if (lv in levels) levels - lv else levels + lv },
                                label = { Text(lv) })
                        }
                    }
                    if (subtopics.size > 1) Row(
                        Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subtopics.forEach { s ->
                            FilterChip(sub == s, { sub = if (sub == s) null else s }, label = { Text(s) })
                        }
                    }
                    Text("${shown.size} Wörter", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(shown, key = { it.id }) { w ->
                ListItem(
                    overlineContent = { Text("${w.level} · ${w.status.emoji} ${w.status.label}" + if (w.subtopic.isNotBlank()) " · ${w.subtopic}" else "") },
                    headlineContent = { GermanText(w, MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text(w.translation) },
                    trailingContent = {
                        IconButton({ speak(w.speakText) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Aussprechen") }
                    },
                    modifier = Modifier.clickable { editing = w }
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (adding) WordDialog(
        word = null, defaultCategory = category ?: "Eigene Wörter",
        onDismiss = { adding = false }, onSave = { vm.addWord(it); adding = false }
    )
    editing?.let { w ->
        WordDialog(
            word = w, onDismiss = { editing = null },
            onSave = { vm.editWord(it); editing = null },
            onDelete = { vm.deleteWord(w.id); editing = null },
            onStatus = { s -> vm.setStatus(w, s); editing = null },
            speak = speak
        )
    }
}

/** Add a new word (word = null) or view/edit an existing one. */
@Composable
fun WordDialog(
    word: Word?,
    initialGerman: String = "",
    defaultCategory: String = "Eigene Wörter",
    onDismiss: () -> Unit,
    onSave: (Word) -> Unit,
    onDelete: (() -> Unit)? = null,
    onStatus: ((Status) -> Unit)? = null,
    speak: ((String) -> Unit)? = null
) {
    var de by remember { mutableStateOf(word?.german ?: initialGerman) }
    var pl by remember { mutableStateOf(word?.plural ?: "") }
    var gr by remember { mutableStateOf(word?.grammar ?: "") }
    var tr by remember { mutableStateOf(word?.translation ?: "") }
    var cat by remember { mutableStateOf(word?.category ?: defaultCategory) }
    var sub by remember { mutableStateOf(word?.subtopic ?: "") }
    var lv by remember { mutableStateOf(word?.level ?: "B1") }
    var rg by remember { mutableStateOf(word?.register ?: "") }
    var ex by remember { mutableStateOf(word?.example ?: "") }
    var exEn by remember { mutableStateOf(word?.exampleEn ?: "") }
    var note by remember { mutableStateOf(word?.note ?: "") }
    var edit by remember { mutableStateOf(word == null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (word == null) "Wort hinzufügen" else if (edit) "Wort bearbeiten" else "Wort") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (word != null && !edit) {
                    WordTags(word, showStatus = true)
                    GermanText(word, MaterialTheme.typography.headlineSmall)
                    Text(word.translation, style = MaterialTheme.typography.titleMedium)
                    WordDetails(word, speak ?: {})
                    onStatus?.let { cb ->
                        HorizontalDivider()
                        Text("Status ändern", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Status.entries.forEach { s ->
                                FilterChip(word.status == s, { cb(s) }, label = { Text(s.emoji + " " + s.label) })
                            }
                        }
                    }
                    Row {
                        TextButton({ edit = true }) { Text("Bearbeiten") }
                        onDelete?.let { TextButton({ confirmDelete = true }) { Text("Löschen", color = MaterialTheme.colorScheme.error) } }
                    }
                } else {
                    OutlinedTextField(de, { de = it }, label = { Text("Deutsch (Nomen mit Artikel) *") }, singleLine = true)
                    OutlinedTextField(tr, { tr = it }, label = { Text("Übersetzung *") }, singleLine = true)
                    OutlinedTextField(pl, { pl = it }, label = { Text("Plural (z. B. die Termine)") }, singleLine = true)
                    OutlinedTextField(gr, { gr = it }, label = { Text("Grammatik (Verbformen, + Akk/Dat)") }, singleLine = true)
                    OutlinedTextField(ex, { ex = it }, label = { Text("Beispielsatz") })
                    OutlinedTextField(exEn, { exEn = it }, label = { Text("Übersetzung des Beispielsatzes") })
                    OutlinedTextField(note, { note = it }, label = { Text("Notiz / Gebrauch") })
                    OutlinedTextField(cat, { cat = it }, label = { Text("Thema") }, singleLine = true)
                    OutlinedTextField(sub, { sub = it }, label = { Text("Unterthema") }, singleLine = true)
                    Text("Niveau", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LEVELS.forEach { l -> FilterChip(lv == l, { lv = l }, label = { Text(l) }) }
                    }
                    Text("Stil", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("" to "neutral", "umg" to "umgangssprachlich", "formell" to "formell").forEach { (v, l) ->
                            FilterChip(rg == v, { rg = v }, label = { Text(l) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (edit) TextButton(
                onClick = {
                    val base = word ?: Word(id = 0, key = "", german = "", translation = "", category = "")
                    onSave(
                        base.copy(
                            german = de.trim(), plural = pl.trim(), grammar = gr.trim(), translation = tr.trim(),
                            category = cat.trim().ifEmpty { "Eigene Wörter" }, subtopic = sub.trim(), level = lv,
                            register = rg, example = ex.trim(), exampleEn = exEn.trim(), note = note.trim()
                        )
                    )
                },
                enabled = de.isNotBlank() && tr.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onDismiss) { Text(if (edit) "Abbrechen" else "Schließen") } }
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Wort löschen?") },
        text = { Text("„${word?.german}“ und sein Lernfortschritt werden gelöscht.") },
        confirmButton = { TextButton({ confirmDelete = false; onDelete?.invoke() }) { Text("Löschen") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen") } }
    )
}
